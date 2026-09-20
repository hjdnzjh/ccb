package com.water.ai.meter.diagnosis;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.util.*;
import static com.water.ai.meter.diagnosis.DiagnosisSupport.*;

@Service
public class WorkOrderVerificationService {
    private final JdbcTemplate jdbc;private final Clock clock;private final TransactionTemplate tx;
    public WorkOrderVerificationService(JdbcTemplate jdbc,ObjectMapper json,Clock clock,PlatformTransactionManager manager){this.jdbc=jdbc;this.clock=clock;tx=new TransactionTemplate(manager);}
    public void startForOrder(long order){tx.executeWithoutResult(s->{
        var cases=jdbc.queryForList("SELECT c.*,m.meter_no FROM diagnosis_case c JOIN water_meter m ON m.id=c.meter_id JOIN work_order w ON w.id=c.work_order_id WHERE w.id=? AND w.status=3 FOR UPDATE",order);
        if(cases.isEmpty())return;var c=cases.get(0);long id=number(c,"id");var evidence=jdbc.queryForList("SELECT baseline_json FROM diagnosis_evidence WHERE case_id=? ORDER BY id DESC LIMIT 1",id);
        var baseline=evidence.isEmpty()?"{}":evidence.get(0).get("baseline_json");LocalDateTime now=LocalDateTime.now(clock);
        jdbc.update("INSERT INTO work_order_verification(case_id,work_order_id,started_at,deadline_at,policy_snapshot,baseline_snapshot) VALUES(?,?,?,?,?,?) ON DUPLICATE KEY UPDATE work_order_id=work_order_id",id,order,now,now.plusHours(72),json(Map.of("meterId",c.get("meter_id"),"meterNo",c.get("meter_no"),"userId",c.get("user_id"),"family",c.get("family"),"requiredCoverage",.9,"hours",24)),baseline);
        jdbc.update("UPDATE diagnosis_case SET state='awaiting_verification' WHERE id=?",id);
    });}
    public List<Map<String,Object>> list(long order){return jdbc.queryForList("SELECT * FROM work_order_verification WHERE work_order_id=? ORDER BY revision DESC",order);}
    public void tick(){for(var row:jdbc.queryForList("SELECT id FROM work_order_verification WHERE state='observing' ORDER BY id LIMIT 100"))tx.executeWithoutResult(s->evaluate(number(row,"id")));}
    private void evaluate(long id){
        var v=jdbc.queryForMap("SELECT * FROM work_order_verification WHERE id=? FOR UPDATE",id);if(!"observing".equals(v.get("state")))return;
        var p=map(v.get("policy_snapshot"));long meter=((Number)p.get("meterId")).longValue();LocalDateTime now=LocalDateTime.now(clock),start=time(v.get("started_at"));
        if(now.isBefore(start))return;LocalDateTime deadline=time(v.get("deadline_at")),cutoff=now.isAfter(deadline)?deadline:now;
        LocalDateTime windowStart=cutoff.minusHours(24).isAfter(start)?cutoff.minusHours(24):start;
        var meters=jdbc.queryForList("SELECT * FROM water_meter WHERE id=? AND deleted=0",meter);boolean comparable=!meters.isEmpty()&&number(meters.get(0),"status")<2&&Objects.equals(meters.get(0).get("meter_no"),p.get("meterNo"))&&number(meters.get(0),"user_id")==((Number)p.get("userId")).longValue();
        var observations=jdbc.queryForList("SELECT * FROM meter_observation WHERE meter_id=? AND quality_status='valid' AND reported_at>? AND reported_at<=? ORDER BY reported_at",meter,windowStart,cutoff);
        Map<Long,Map<String,Object>> slots=new TreeMap<>();for(var o:observations)slots.put(time(o.get("reported_at")).atZone(clock.getZone()).toEpochSecond()/900,o);
        var baseline=map(v.get("baseline_snapshot"));boolean usage="usage".equals(p.get("family"));
        var groups=map(baseline.get("groups"));boolean baselineAvailable=usage?!groups.isEmpty():Set.of("high_flow","device").contains(p.get("family"));
        var anchors=jdbc.queryForList("SELECT * FROM meter_observation WHERE meter_id=? AND quality_status='valid' AND reported_at<=? ORDER BY reported_at DESC LIMIT 1",meter,windowStart);
        Map<String,Object> previousObservation=anchors.isEmpty()?null:anchors.get(0);
        boolean hard=false,nightSeen=false;int high=0,normal=0,comparableSamples=0;long previousSlot=-2,maxGap=0;LocalDateTime previous=windowStart;
        // Slot compaction must never hide an earlier device alarm or closed valve.
        for(var raw:observations){
            double rawFlow=((Number)raw.get("flow")).doubleValue(),temperature=((Number)raw.get("temperature")).doubleValue();
            if(!"OPEN".equals(raw.get("valve")))comparable=false;
            hard|=!"0".equals(raw.get("alarm"))||("CLOSED".equals(raw.get("valve"))&&rawFlow>0)||temperature>60||temperature<0;
        }
        for(var entry:slots.entrySet()){
            var o=entry.getValue();var at=time(o.get("reported_at"));double flow=((Number)o.get("flow")).doubleValue();
            if(!"OPEN".equals(o.get("valve")))comparable=false;
            hard|=!"0".equals(o.get("alarm"))||("CLOSED".equals(o.get("valve"))&&flow>0)||((Number)o.get("temperature")).doubleValue()>60||((Number)o.get("temperature")).doubleValue()<0;
            Double value=flow,upper=baselineAvailable?10.0:null;
            if(usage){
                var group=map(groups.get((at.getDayOfWeek().getValue()>=6?"1":"0")+":"+(at.getHour()/4)));
                upper=Boolean.TRUE.equals(group.get("ready"))&&group.get("upper") instanceof Number n?n.doubleValue():null;value=null;
                if(previousObservation!=null&&Objects.equals(previousObservation.get("source"),o.get("source"))){
                    long seconds=Duration.between(time(previousObservation.get("reported_at")),at).toSeconds();
                    double delta=((Number)o.get("total")).doubleValue()-((Number)previousObservation.get("total")).doubleValue();
                    long oldSlot=time(previousObservation.get("reported_at")).atZone(clock.getZone()).toEpochSecond()/900;
                    if(seconds>0&&seconds<=1800&&entry.getKey()==oldSlot+1&&delta>=0)value=delta*3600/seconds;
                }
            }
            previousObservation=o;
            boolean valid=value!=null&&upper!=null;
            if(valid){maxGap=Math.max(maxGap,Duration.between(previous,at).toSeconds());previous=at;comparableSamples++;}
            boolean abnormal=valid&&value>upper;
            high=abnormal?(entry.getKey()==previousSlot+1?high+1:1):0;previousSlot=entry.getKey();if(high>=3)hard=true;
            if(valid&&!abnormal)normal++;
            if(valid&&at.getHour()>=2&&at.getHour()<5)nightSeen=true;
        }
        maxGap=Math.max(maxGap,Duration.between(previous,cutoff).toSeconds());long expected=Math.max(1,(long)Math.ceil(Duration.between(windowStart,cutoff).toSeconds()/900.0));double coverage=Math.min(1,comparableSamples/(double)expected);
        boolean elapsed=!cutoff.isBefore(start.plusHours(24));boolean enough=elapsed&&coverage>=.9&&maxGap<=1800&&comparable&&baselineAvailable;
        if(p.get("family").toString().contains("night"))enough&=nightSeen;
        String state=hard?"persistent":enough&&normal>=slots.size()*.9?"recovered":!now.isBefore(time(v.get("deadline_at")))?"inconclusive":"observing";
        String reason=switch(state){case "persistent"->"观察到新的设备报警或连续异常窗口，需要现场复核";case "recovered"->"观察期数据充分，原异常条件已解除；这不是现场维修证明";case "inconclusive"->"观察期限已到，数据覆盖、基线或供水条件不足以判断恢复";default->"等待完整观察窗口和可比较的数据";};
        jdbc.update("UPDATE work_order_verification SET state=?,coverage=?,result_json=?,updated_at=? WHERE id=?",state,coverage,json(Map.of("reason",reason,"samples",comparableSamples,"expectedSamples",expected,"maxGapSeconds",maxGap,"comparable",comparable,"hasBaseline",baselineAvailable,"windowStart",windowStart.toString(),"windowEnd",cutoff.toString())),now,id);
        if(!"observing".equals(state))jdbc.update("UPDATE diagnosis_case SET state=?,closed_at=?,summary=? WHERE id=?",state.equals("recovered")?"closed":"needs_review",state.equals("recovered")?now:null,reason,v.get("case_id"));
    }
    public long followup(long order,String note,String requestKey,long actor){key(requestKey);String text=note(note);return tx.execute(s->{
        var rows=jdbc.queryForList("SELECT * FROM work_order_verification WHERE work_order_id=? ORDER BY revision DESC LIMIT 1 FOR UPDATE",order);if(rows.isEmpty())throw new IllegalArgumentException("工单没有核验记录");var v=rows.get(0);
        var previous=jdbc.queryForList("SELECT * FROM diagnosis_review WHERE request_key=?",requestKey);
        if(!previous.isEmpty()&&(number(previous.get(0),"case_id")!=number(v,"case_id")||number(previous.get(0),"reviewer_id")!=actor||!text.equals(previous.get(0).get("note"))))throw new IllegalArgumentException("请求号已用于不同复核操作");
        if(v.get("followup_order_id")!=null)return number(v,"followup_order_id");
        if(!Set.of("persistent","inconclusive").contains(v.get("state")))throw new IllegalArgumentException("仅仍有异常或无法判断的记录可创建复检工单");
        var c=jdbc.queryForMap("SELECT * FROM diagnosis_case WHERE id=? FOR UPDATE",v.get("case_id"));String token=UUID.randomUUID().toString().replace("-","");
        jdbc.update("INSERT INTO anomaly_record(anomaly_no,meter_id,user_id,anomaly_type,severity,description,status,detected_time) VALUES(?,?,?,'verification_failure','high',?,1,?)","VERIFY-"+token,c.get("meter_id"),c.get("user_id"),"原工单 #"+order+" 效果复检："+text.substring(0,Math.min(350,text.length())),LocalDateTime.now(clock));
        long anomaly=jdbc.queryForObject("SELECT id FROM anomaly_record WHERE anomaly_no=?",Long.class,"VERIFY-"+token);
        jdbc.update("INSERT INTO work_order(order_no,anomaly_id,meter_id,user_id,title,description,priority,status) VALUES(?,?,?,?,?,?,2,0)","WO-"+token,anomaly,c.get("meter_id"),c.get("user_id"),"处置效果复检：原工单 #"+order,text);
        long next=jdbc.queryForObject("SELECT id FROM work_order WHERE order_no=?",Long.class,"WO-"+token);jdbc.update("UPDATE anomaly_record SET work_order_id=? WHERE id=?",next,anomaly);
        jdbc.update("UPDATE work_order_verification SET followup_anomaly_id=?,followup_order_id=? WHERE id=?",anomaly,next,v.get("id"));
        jdbc.update("INSERT INTO diagnosis_review(case_id,reviewer_id,label,note,request_key) VALUES(?,?,'followup',?,?)",c.get("id"),actor,text,requestKey);
        jdbc.update("UPDATE diagnosis_case SET state='work_order_linked',work_order_id=? WHERE id=?",next,c.get("id"));return next;
    });}
}
