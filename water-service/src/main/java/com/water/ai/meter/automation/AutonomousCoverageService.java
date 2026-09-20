package com.water.ai.meter.automation;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;

/** Admin-configured coverage policy, automatically plans newly eligible meters. */
@Service
public class AutonomousCoverageService {
 private final JdbcTemplate jdbc;private final CollectionService collection;
 public AutonomousCoverageService(JdbcTemplate jdbc,CollectionService collection){this.jdbc=jdbc;this.collection=collection;}
 public record Input(boolean enabled,int intervalMinutes,BigDecimal incrementAmount){}
 public Map<String,Object> policy(){return jdbc.queryForMap("SELECT * FROM automation_coverage_policy WHERE id=1");}
 @Transactional public void save(Input input){
  if(input.intervalMinutes()<1 || input.intervalMinutes()>43200 || input.incrementAmount()==null || input.incrementAmount().signum()<=0 || input.incrementAmount().scale()>2 || input.incrementAmount().compareTo(new BigDecimal("100000"))>0)throw new IllegalArgumentException("间隔须为1至43200分钟，模拟增量大于0且最多两位小数");
  jdbc.update("UPDATE automation_coverage_policy SET enabled=?,interval_minutes=?,increment_amount=? WHERE id=1",input.enabled(),input.intervalMinutes(),input.incrementAmount());
  reconcile();
 }
 @Transactional public int reconcile(){
  var policy=jdbc.queryForMap("SELECT * FROM automation_coverage_policy WHERE id=1 FOR UPDATE");
  boolean enabled=Boolean.TRUE.equals(policy.get("enabled")) || "1".equals(String.valueOf(policy.get("enabled")));
  if(!enabled){jdbc.update("UPDATE automation_plan p JOIN automation_coverage_meter c ON c.plan_id=p.id SET p.enabled=0");return 0;}
  var eligible=jdbc.queryForList("SELECT m.id,m.meter_no FROM water_meter m JOIN sys_user u ON u.id=m.user_id AND u.deleted=0 AND u.status=0 WHERE m.deleted=0 AND m.status IN(0,1) ORDER BY m.id");
  Set<Long> eligibleIds=new HashSet<>();for(var m:eligible)eligibleIds.add(((Number)m.get("id")).longValue());
  Map<Long,Long> managed=new HashMap<>();for(var row:jdbc.queryForList("SELECT * FROM automation_coverage_meter"))managed.put(((Number)row.get("meter_id")).longValue(),((Number)row.get("plan_id")).longValue());
  for(var entry:managed.entrySet())if(!eligibleIds.contains(entry.getKey()))jdbc.update("UPDATE automation_plan SET enabled=0 WHERE id=?",entry.getValue());
  int created=0;
  for(var meter:eligible){
   long id=((Number)meter.get("id")).longValue();Long plan=managed.get(id);
   long manual=jdbc.queryForObject("SELECT COUNT(*) FROM automation_plan p JOIN automation_plan_meter pm ON pm.plan_id=p.id WHERE pm.meter_id=? AND p.enabled=1 AND p.id<>?",Long.class,id,plan==null?-1:plan);
   if(manual>0){if(plan!=null)jdbc.update("UPDATE automation_plan SET enabled=0 WHERE id=?",plan);continue;}
   if(plan==null){
    if(created>=100)continue;
    plan=collection.createPlan(new CollectionService.PlanInput("自主覆盖 · "+meter.get("meter_no"),List.of(id),((Number)policy.get("interval_minutes")).intValue(),true,3,30,"normal",new BigDecimal(policy.get("increment_amount").toString())));
    jdbc.update("INSERT INTO automation_coverage_meter(meter_id,plan_id) VALUES(?,?)",id,plan);created++;
   }else jdbc.update("UPDATE automation_plan SET enabled=1,interval_minutes=?,increment_amount=? WHERE id=?",policy.get("interval_minutes"),policy.get("increment_amount"),plan);
  }
  return created;
 }
}
