package com.water.ai.meter.diagnosis;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.math.*;
import java.util.*;
import static com.water.ai.meter.diagnosis.DiagnosisSupport.*;

@Service
public class DiagnosticProbeService {
 private final JdbcTemplate jdbc;private final Clock clock;private final TransactionTemplate tx;private final ObservationService observations;
 public DiagnosticProbeService(JdbcTemplate jdbc,Clock clock,PlatformTransactionManager manager,ObservationService observations){this.jdbc=jdbc;this.clock=clock;this.observations=observations;tx=new TransactionTemplate(manager);}
 public void tick(){var now=LocalDateTime.now(clock);for(var p:jdbc.queryForList("SELECT id FROM diagnostic_probe WHERE (state IN ('pending','retry') AND due_at<=?) OR (state='running' AND lease_until<=?) ORDER BY due_at,id LIMIT 10",now,now))process(number(p,"id"));finishCases();}
 public void process(long id){String lease=UUID.randomUUID().toString();var now=LocalDateTime.now(clock);
  Map<String,Object> p=tx.execute(s->{
   // Shared policy row serializes request admission, including global/daily budgets.
   var policy=jdbc.queryForMap("SELECT * FROM diagnosis_policy WHERE id=1 FOR UPDATE");
   var probe=jdbc.queryForMap("SELECT * FROM diagnostic_probe WHERE id=? FOR UPDATE",id);
   if(Set.of("success","failed","cancelled").contains(probe.get("state"))||time(probe.get("due_at")).isAfter(now)||("running".equals(probe.get("state"))&&time(probe.get("lease_until")).isAfter(now)))return null;
   var c=jdbc.queryForMap("SELECT c.*,m.status meter_status,m.deleted,m.meter_no FROM diagnosis_case c JOIN water_meter m ON m.id=c.meter_id WHERE c.id=? FOR UPDATE",probe.get("case_id"));long meter=number(c,"meter_id");
   if(!"assisted".equals(policy.get("mode"))||number(policy,"version")!=number(c,"policy_version")||!ids(policy.get("meter_ids")).contains(meter)||!"probing".equals(c.get("state"))||number(c,"meter_status")>1||number(c,"deleted")!=0){jdbc.update("UPDATE diagnostic_probe SET state='cancelled',error='策略、事件或水表状态不允许继续复测' WHERE id=?",id);return null;}
   if(number(probe,"attempts")>=2){jdbc.update("UPDATE diagnostic_probe SET state='failed',error='请求预算已耗尽' WHERE id=?",id);return null;}
   if(jdbc.queryForObject("SELECT COUNT(*) FROM diagnostic_probe WHERE state='running' AND lease_until>?",Long.class,now)>=10)return null;
   jdbc.update("INSERT INTO diagnostic_budget(meter_id,budget_day) VALUES(?,?) ON DUPLICATE KEY UPDATE meter_id=meter_id",meter,now.toLocalDate());
   var budget=jdbc.queryForMap("SELECT * FROM diagnostic_budget WHERE meter_id=? AND budget_day=? FOR UPDATE",meter,now.toLocalDate());
   if(number(budget,"requests")>=24){jdbc.update("UPDATE diagnostic_probe SET state='failed',error='单表当日24次额外请求预算耗尽' WHERE id=?",id);return null;}
   jdbc.update("UPDATE diagnostic_budget SET requests=requests+1 WHERE meter_id=? AND budget_day=?",meter,now.toLocalDate());
   jdbc.update("UPDATE diagnostic_probe SET state='running',attempts=attempts+1,lease_token=?,lease_until=? WHERE id=?",lease,now.plusSeconds(60),id);
   var result=new HashMap<>(probe);result.putAll(Map.of("meter_id",meter,"meter_no",c.get("meter_no"),"actor",c.get("user_id")));return result;
  });if(p==null)return;
  Long observed=null;String problem=null;
  try{
   var rows=jdbc.queryForList("SELECT o.* FROM diagnosis_evidence e JOIN meter_observation o ON o.id=e.observation_id WHERE e.case_id=? ORDER BY e.id LIMIT 1",p.get("case_id"));if(rows.isEmpty())throw new IllegalArgumentException("缺少模拟轨迹起点，无法复测");var anchor=rows.get(0);
   // Explicit constant-flow simulation anchored once. Sampling cannot add usage.
   if(!"simulated".equals(anchor.get("source")))throw new IllegalArgumentException("尚未配置真实设备诊断采集适配器");
   var at=now.withNano(0);var anchorAt=time(anchor.get("reported_at"));if(!at.isAfter(anchorAt))throw new IllegalArgumentException("尚未取得新的设备时刻");
   BigDecimal flow=new BigDecimal(anchor.get("flow").toString());BigDecimal total=new BigDecimal(anchor.get("total").toString()).add(flow.multiply(BigDecimal.valueOf(Duration.between(anchorAt,at).toSeconds())).divide(BigDecimal.valueOf(3600),8,RoundingMode.HALF_UP)).setScale(2,RoundingMode.HALF_UP);
   String packet=p.get("meter_no")+"|"+at+"|"+flow+"|"+total+"|"+anchor.get("temperature")+"|"+anchor.get("valve")+"|"+anchor.get("alarm");
   observed=((Number)observations.ingest(packet,"probe:"+id+":"+(number(p,"attempts")+1),number(p,"actor")).get("observationId")).longValue();
  }catch(RuntimeException e){observed=null;problem=Objects.toString(e.getMessage(),"模拟复测失败");}
  final Long observation=observed;final String failure=problem;
  tx.executeWithoutResult(s->{var activePolicy=jdbc.queryForMap("SELECT * FROM diagnosis_policy WHERE id=1 FOR UPDATE");var current=jdbc.queryForMap("SELECT * FROM diagnostic_probe WHERE id=? FOR UPDATE",id);if(!lease.equals(current.get("lease_token")))return;
   boolean duplicate=observation!=null&&jdbc.queryForObject("SELECT COUNT(*) FROM diagnostic_probe WHERE case_id=? AND observation_id=? AND id<>?",Long.class,p.get("case_id"),observation,id)>0;
   Long accepted=duplicate?null:observation;String reason=duplicate?"重复设备时刻，未取得新的复测证据":failure;
   var activeCase=jdbc.queryForMap("SELECT state,policy_version FROM diagnosis_case WHERE id=?",p.get("case_id"));
   boolean retryAllowed="assisted".equals(activePolicy.get("mode"))&&number(activePolicy,"version")==number(activeCase,"policy_version")&&"probing".equals(activeCase.get("state"));
   String state=accepted!=null?"success":!retryAllowed?"cancelled":number(current,"attempts")<2?"retry":"failed";
   jdbc.update("UPDATE diagnostic_probe SET state=?,observation_id=?,error=?,due_at=?,lease_until=NULL WHERE id=?",state,accepted,reason==null?"恒流模拟复测（非真实设备）":reason.substring(0,Math.min(500,reason.length())),now.plusSeconds(60),id);
  });
 }
 private void finishCases(){for(var row:jdbc.queryForList("SELECT id FROM diagnosis_case WHERE state='probing' AND NOT EXISTS(SELECT 1 FROM diagnostic_probe p WHERE p.case_id=diagnosis_case.id AND p.state IN ('pending','retry','running')) LIMIT 100"))tx.executeWithoutResult(s->{
  var c=jdbc.queryForMap("SELECT * FROM diagnosis_case WHERE id=? FOR UPDATE",row.get("id"));if(!"probing".equals(c.get("state")))return;
  var probes=jdbc.queryForList("SELECT * FROM diagnostic_probe WHERE case_id=?",c.get("id"));if(probes.isEmpty())return;
  // Always require human judgment after the bounded simulation. No assumed recovery.
  String summary=probes.stream().allMatch(p->"success".equals(p.get("state")))?"三次模拟复测已完成，请结合新证据人工核查":"复测结束，但存在失败、取消或预算限制；证据不足，需人工核查";
  jdbc.update("UPDATE diagnosis_case SET state='needs_review',summary=? WHERE id=?",summary,c.get("id"));
 });}
}
