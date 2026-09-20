package com.water.ai.meter.diagnosis;

import com.water.ai.meter.operations.AnomalyOperations;
import com.water.ai.meter.security.SessionUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;
import static com.water.ai.meter.diagnosis.DiagnosisSupport.*;

@Service
public class DiagnosisService {
 private final JdbcTemplate jdbc;private final Clock clock;private final TransactionTemplate tx;private final ObservationService observations;private final DiagnosisClient client;private final AnomalyOperations anomalies;
 public DiagnosisService(JdbcTemplate jdbc,Clock clock,PlatformTransactionManager manager,ObservationService observations,DiagnosisClient client,AnomalyOperations anomalies){this.jdbc=jdbc;this.clock=clock;this.observations=observations;this.client=client;this.anomalies=anomalies;tx=new TransactionTemplate(manager);}
 public record Policy(long version,String mode,List<Long> meterIds){}
 public Map<String,Object> policy(){var row=jdbc.queryForMap("SELECT * FROM diagnosis_policy WHERE id=1");return Map.of("version",row.get("version"),"mode",row.get("mode"),"meterIds",ids(row.get("meter_ids")));}
 public Map<String,Object> savePolicy(Policy input,long actor){
  if(!Set.of("off","shadow","assisted").contains(Objects.toString(input.mode(),""))||input.meterIds()==null||input.meterIds().size()>1000||input.meterIds().stream().anyMatch(Objects::isNull))throw new IllegalArgumentException("策略模式或水表范围无效，最多1000块表");
  return tx.execute(s->{var p=jdbc.queryForMap("SELECT * FROM diagnosis_policy WHERE id=1 FOR UPDATE");if(number(p,"version")!=input.version())throw new ResponseStatusException(HttpStatus.CONFLICT,"策略已被更新，请刷新后重试");
   var meters=input.meterIds().stream().distinct().sorted().toList();if(!input.mode().equals("off")&&meters.isEmpty())throw new IllegalArgumentException("请明确选择诊断水表范围");
   for(long meter:meters)if(jdbc.queryForObject("SELECT COUNT(*) FROM water_meter m JOIN sys_user u ON u.id=m.user_id WHERE m.id=? AND m.deleted=0 AND m.status IN (0,1) AND u.deleted=0 AND u.status=0",Integer.class,meter)!=1)throw new IllegalArgumentException("诊断范围包含停用或无效水表："+meter);
   long version=input.version()+1;jdbc.update("UPDATE diagnosis_policy SET version=?,mode=?,meter_ids=?,updated_by=?,updated_at=? WHERE id=1",version,input.mode(),json(meters),actor,LocalDateTime.now(clock));
   jdbc.update("INSERT INTO diagnosis_policy_audit(version,mode,meter_ids,actor_id) VALUES(?,?,?,?)",version,input.mode(),json(meters),actor);
   jdbc.update("UPDATE diagnosis_job SET state='cancelled' WHERE state IN ('pending','retry','running') AND policy_version<>?",version);
   jdbc.update("UPDATE diagnostic_probe SET state='cancelled',error='诊断策略已变更，待发送任务取消' WHERE state IN ('pending','retry')");
   jdbc.update("UPDATE diagnosis_case SET state='needs_review',summary='策略已变更，原复测暂停；可人工核查' WHERE state='probing'");return policy();
  });
 }
 public Map<String,Object> cases(int page,int size,String meterNo,String state,Long owner){
  if(page<1||size<1||size>100)throw new IllegalArgumentException("分页参数无效");List<Object> args=new ArrayList<>();String where=" FROM diagnosis_case c JOIN water_meter m ON m.id=c.meter_id WHERE 1=1";
  if(owner!=null){where+=" AND c.user_id=? AND m.user_id=? AND m.deleted=0 AND c.mode='assisted'";args.add(owner);args.add(owner);}
  if(meterNo!=null&&!meterNo.isBlank()){where+=" AND m.meter_no LIKE ?";args.add("%"+meterNo.trim()+"%");}
  if(state!=null&&!state.isBlank()){where+=" AND c.state=?";args.add(state);}
  long total=jdbc.queryForObject("SELECT COUNT(*)"+where,Long.class,args.toArray());args.add(size);args.add((long)(page-1)*size);
  var rows=jdbc.queryForList("SELECT c.*,m.meter_no"+where+" ORDER BY c.id DESC LIMIT ? OFFSET ?",args.toArray());
  if(owner!=null)return Map.of("records",rows.stream().map(this::simplified).toList(),"total",total);
  return Map.of("records",rows,"total",total);
 }
 private Map<String,Object> simplified(Map<String,Object> row){var out=new LinkedHashMap<String,Object>();out.put("id",row.get("id"));out.put("meterNo",row.get("meter_no"));out.put("state",row.get("state"));out.put("summary",row.get("summary"));out.put("updatedAt",row.get("last_evidence_at"));var verification=jdbc.queryForList("SELECT state FROM work_order_verification WHERE case_id=? ORDER BY id DESC LIMIT 1",row.get("id"));out.put("verificationState",verification.isEmpty()?null:verification.get(0).get("state"));return out;}
 public Map<String,Object> detail(long id,Long owner){var rows=jdbc.queryForList("SELECT c.*,m.meter_no,m.deleted AS meter_deleted,m.user_id AS current_owner FROM diagnosis_case c JOIN water_meter m ON m.id=c.meter_id WHERE c.id=?",id);if(rows.isEmpty()||(owner!=null&&(number(rows.get(0),"user_id")!=owner||number(rows.get(0),"current_owner")!=owner||number(rows.get(0),"meter_deleted")!=0||!"assisted".equals(rows.get(0).get("mode")))))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"诊断记录不存在");var c=new LinkedHashMap<>(rows.get(0));if(owner!=null)return simplified(c);
  c.put("evidence",jdbc.queryForList("SELECT * FROM diagnosis_evidence WHERE case_id=? ORDER BY id DESC LIMIT 100",id));c.put("probes",jdbc.queryForList("SELECT * FROM diagnostic_probe WHERE case_id=? ORDER BY sequence_no",id));c.put("reviews",jdbc.queryForList("SELECT * FROM diagnosis_review WHERE case_id=? ORDER BY id DESC LIMIT 100",id));c.put("verifications",jdbc.queryForList("SELECT * FROM work_order_verification WHERE case_id=? ORDER BY id DESC",id));return c;
 }
 public void tick(){for(var row:jdbc.queryForList("SELECT id FROM diagnosis_job WHERE (state IN ('pending','retry') AND next_at<=?) OR (state='running' AND lease_until<=?) ORDER BY id LIMIT 20",LocalDateTime.now(clock),LocalDateTime.now(clock)))process(number(row,"id"));}
 public void process(long id){
  String lease=UUID.randomUUID().toString();Map<String,Object> job=tx.execute(s->{var row=jdbc.queryForMap("SELECT * FROM diagnosis_job WHERE id=? FOR UPDATE",id);var now=LocalDateTime.now(clock);if(Set.of("done","cancelled").contains(row.get("state"))||("running".equals(row.get("state"))&&time(row.get("lease_until")).isAfter(now))||time(row.get("next_at")).isAfter(now))return null;
   var p=policy();if(!Objects.equals(p.get("version"),row.get("policy_version"))||"off".equals(p.get("mode"))){jdbc.update("UPDATE diagnosis_job SET state='cancelled' WHERE id=?",id);return null;}
   jdbc.update("UPDATE diagnosis_job SET state='running',attempts=attempts+1,lease_token=?,lease_until=? WHERE id=?",lease,now.plusSeconds(60),id);return row;});if(job==null)return;
  var o=jdbc.queryForMap("SELECT * FROM meter_observation WHERE id=?",job.get("observation_id"));Map<String,Object> result;String failure=null;
  try{result=client.score(number(o,"meter_id"),observations.history(number(o,"meter_id"),time(o.get("reported_at"))));}catch(RuntimeException e){failure="诊断引擎不可用，已使用设备基础规则";result=client.fallback(o);}
  final var score=result;final String problem=failure;
  tx.executeWithoutResult(s->{jdbc.queryForMap("SELECT id FROM diagnosis_policy WHERE id=1 FOR UPDATE");var current=jdbc.queryForMap("SELECT * FROM diagnosis_job WHERE id=? FOR UPDATE",id);if(!"running".equals(current.get("state"))||!lease.equals(current.get("lease_token")))return;
   var meter=jdbc.queryForMap("SELECT * FROM water_meter WHERE id=?",o.get("meter_id"));
   if(number(meter,"deleted")!=0||number(meter,"status")>1||meter.get("user_id")==null){jdbc.update("UPDATE diagnosis_job SET state='cancelled' WHERE id=?",id);return;}
   apply(o,meter,job,score);boolean retry=problem!=null&&number(current,"attempts")<3;
   jdbc.update("UPDATE diagnosis_job SET state=?,next_at=?,last_error=?,lease_until=NULL WHERE id=?",retry?"retry":"done",LocalDateTime.now(clock).plusSeconds(30),problem,id);
  });
 }
 private void apply(Map<String,Object> o,Map<String,Object> meter,Map<String,Object> job,Map<String,Object> result){
  String reasons=json(result.get("reasonCodes"));boolean hard=reasons.contains("device_alarm")||reasons.contains("high_flow")||reasons.contains("flow_while_valve_closed")||reasons.contains("temperature_abnormal");String family=hard?"device":"usage";long meterId=number(meter,"id");
  jdbc.update("INSERT INTO diagnosis_case_guard(meter_id,family) VALUES(?,?) ON DUPLICATE KEY UPDATE meter_id=meter_id",meterId,family);
  var guard=jdbc.queryForMap("SELECT * FROM diagnosis_case_guard WHERE meter_id=? AND family=? FOR UPDATE",meterId,family);Map<String,Object> c=null;
  if(guard.get("active_case_id")!=null){var found=jdbc.queryForList("SELECT * FROM diagnosis_case WHERE id=? FOR UPDATE",guard.get("active_case_id"));if(!found.isEmpty()&&!"closed".equals(found.get(0).get("state")))c=found.get(0);}
  if(c!=null&&(number(c,"policy_version")!=number(job,"policy_version")||number(c,"user_id")!=number(meter,"user_id")||!Objects.equals(c.get("mode"),job.get("mode"))))c=null;
  if(c==null&&!"needs_review".equals(result.get("decision")))return;
  LocalDateTime at=time(o.get("reported_at"));boolean assisted="assisted".equals(job.get("mode"));
  if(c==null){String summary=hard?"设备基础规则命中，请结合报文现场核查":"连续用水偏离个人历史范围，建议补充观测";
   jdbc.update("INSERT INTO diagnosis_case(meter_id,user_id,family,state,severity,summary,mode,policy_version,model_version,opened_at,last_evidence_at) VALUES(?,?,?,?,?,?,?,?,?,?,?)",meterId,meter.get("user_id"),family,assisted?(hard?"needs_review":"probing"):"candidate",hard?"high":"medium",summary,job.get("mode"),job.get("policy_version"),result.get("modelVersion"),at,at);
   long caseId=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);jdbc.update("UPDATE diagnosis_case_guard SET active_case_id=? WHERE meter_id=? AND family=?",caseId,meterId,family);c=jdbc.queryForMap("SELECT * FROM diagnosis_case WHERE id=?",caseId);
   if(assisted){ensureAnomaly(c,o,summary);if(!hard)schedule(c);}
  }
  // A backlog result cannot move a case's evidence time backwards or create future knowledge.
  if(at.isBefore(time(c.get("last_evidence_at"))))return;
  var features=new LinkedHashMap<String,Object>((Map<String,Object>)result.get("features"));features.put("decision",result.get("decision"));features.put("fallbackReason",result.get("fallbackReason"));
  jdbc.update("INSERT INTO diagnosis_evidence(case_id,observation_id,window_end,reason_codes,features_json,baseline_json,model_version,score) VALUES(?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE observation_id=observation_id",c.get("id"),o.get("id"),at,reasons,json(features),json(result.get("baseline")),result.get("modelVersion"),result.get("anomalyScore"));
  jdbc.update("UPDATE diagnosis_case SET last_evidence_at=?,model_version=? WHERE id=?",at,result.get("modelVersion"),c.get("id"));
 }
 private void ensureAnomaly(Map<String,Object> c,Map<String,Object> o,String summary){
  var existing=jdbc.queryForList("SELECT id FROM anomaly_record WHERE meter_id=? AND deleted=0 AND detection_detail=? ORDER BY id LIMIT 1",c.get("meter_id"),o.get("packet"));long id;
  if(existing.isEmpty()){String no="DIAG-"+UUID.randomUUID().toString().replace("-","");jdbc.update("INSERT INTO anomaly_record(anomaly_no,meter_id,user_id,anomaly_type,severity,description,detection_detail,status,detected_time,remark) VALUES(?,?,?,'usage_diagnosis',?,?,?,0,?,'诊断证据；模拟来源')",no,c.get("meter_id"),c.get("user_id"),c.get("severity"),summary,o.get("packet"),o.get("reported_at"));id=jdbc.queryForObject("SELECT id FROM anomaly_record WHERE anomaly_no=?",Long.class,no);}else id=number(existing.get(0),"id");
  jdbc.update("UPDATE diagnosis_case SET anomaly_id=? WHERE id=?",id,c.get("id"));
 }
 void schedule(Map<String,Object> c){long caseId=number(c,"id");if(jdbc.queryForObject("SELECT COUNT(*) FROM diagnostic_probe WHERE case_id=?",Long.class,caseId)>0)return;
  var now=LocalDateTime.now(clock);int i=0;for(int minutes:new int[]{5,15,30})jdbc.update("INSERT INTO diagnostic_probe(case_id,sequence_no,due_at) VALUES(?,?,?)",caseId,++i,now.plusMinutes(minutes));jdbc.update("UPDATE diagnosis_case SET state='probing' WHERE id=?",caseId);
 }
 public Map<String,Object> action(long id,String action,Map<String,String> body,SessionUser actor){
  String request=body.get("requestKey");key(request);String fingerprint=hash(json(new TreeMap<>(body)));
  return tx.execute(s->{jdbc.queryForMap("SELECT id FROM diagnosis_policy WHERE id=1 FOR UPDATE");var c=jdbc.queryForMap("SELECT * FROM diagnosis_case WHERE id=? FOR UPDATE",id);var old=jdbc.queryForList("SELECT * FROM diagnosis_action WHERE request_key=?",request);
   if(!old.isEmpty()){var r=old.get(0);if(number(r,"case_id")!=id||number(r,"actor_id")!=actor.id()||!action.equals(r.get("action"))||!fingerprint.equals(r.get("payload_hash")))throw new ResponseStatusException(HttpStatus.CONFLICT,"请求号已用于不同操作");return map(r.get("result_json"));}
   Map<String,Object> result=new LinkedHashMap<>();result.put("caseId",id);
   switch(action){
    case "probe"->{if(!"assisted".equals(policy().get("mode"))||!"assisted".equals(c.get("mode"))||!ids(json(policy().get("meterIds"))).contains(number(c,"meter_id"))||Set.of("closed","work_order_linked","awaiting_verification").contains(c.get("state")))throw new IllegalArgumentException("当前事件或策略不允许复测");schedule(c);}
    case "cancel-probes"->{String reason=note(body.get("reason"));jdbc.update("UPDATE diagnostic_probe SET state='cancelled',error=? WHERE case_id=? AND state IN ('pending','retry')",reason,id);if("probing".equals(c.get("state")))jdbc.update("UPDATE diagnosis_case SET state='needs_review',summary='复测已取消，等待人工核查' WHERE id=?",id);}
    case "review"->{String label=body.get("label"),text=note(body.get("note"));if(!Set.of("confirmed_anomaly","normal_business","device_fault","insufficient_data").contains(Objects.toString(label,"")))throw new IllegalArgumentException("核查标签无效");jdbc.update("INSERT INTO diagnosis_review(case_id,reviewer_id,label,note,request_key) VALUES(?,?,?,?,?)",id,actor.id(),label,text,request);result.put("label",label);}
    case "work-order"->{if(c.get("work_order_id")!=null){result.put("workOrderId",c.get("work_order_id"));break;}var p=policy();if(c.get("anomaly_id")==null||!"assisted".equals(c.get("mode"))||!"assisted".equals(p.get("mode"))||number(c,"policy_version")!=number(p,"version")||!ids(json(p.get("meterIds"))).contains(number(c,"meter_id"))||"closed".equals(c.get("state")))throw new IllegalArgumentException("当前事件或策略不允许创建工单，请核对策略范围");long work=anomalies.workOrder(number(c,"anomaly_id"));jdbc.update("UPDATE diagnosis_case SET work_order_id=?,state='work_order_linked' WHERE id=?",work,id);jdbc.update("UPDATE diagnostic_probe SET state='cancelled',error='事件已转工单' WHERE case_id=? AND state IN ('pending','retry')",id);result.put("workOrderId",work);}
    default->throw new IllegalArgumentException("未知诊断操作");
   }
   jdbc.update("INSERT INTO diagnosis_action(request_key,actor_id,action,case_id,payload_hash,result_json) VALUES(?,?,?,?,?,?)",request,actor.id(),action,id,fingerprint,json(result));return result;
  });
 }
}
