package com.water.ai.meter.diagnosis;

import com.water.ai.meter.automation.DeviceReport;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static com.water.ai.meter.diagnosis.DiagnosisSupport.*;

@Service
public class ObservationService {
    private final JdbcTemplate jdbc;private final Clock clock;private final TransactionTemplate tx,audit;
    public ObservationService(JdbcTemplate jdbc,Clock clock,PlatformTransactionManager manager){
        this.jdbc=jdbc;this.clock=clock;tx=new TransactionTemplate(manager);tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        audit=new TransactionTemplate(manager);audit.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);audit.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }
    public Map<String,Object> ingest(String packet,String requestKey,long actor){
        key(requestKey);if(packet==null||packet.length()>500)throw new IllegalArgumentException("设备报文为空或过长");
        String fingerprint=hash(packet),lease=UUID.randomUUID().toString();LocalDateTime now=LocalDateTime.now(clock);
        String meterNo=packet.split("\\|",-1)[0];var meters=jdbc.queryForList("SELECT id FROM water_meter WHERE meter_no=? AND deleted=0",meterNo);
        Long meter=meters.isEmpty()?null:number(meters.get(0),"id");
        Map<String,Object> old=audit.execute(s->{
            jdbc.update("INSERT INTO observation_attempt(request_key,meter_id,purpose,packet_hash,actor_id,lease_token,lease_until) VALUES(?,?,'diagnostic',?,?,?,?) ON DUPLICATE KEY UPDATE request_key=request_key",requestKey,meter,fingerprint,actor,lease,now.plusSeconds(60));
            var row=jdbc.queryForMap("SELECT * FROM observation_attempt WHERE request_key=? FOR UPDATE",requestKey);
            if(!fingerprint.equals(row.get("packet_hash"))||number(row,"actor_id")!=actor)throw new ResponseStatusException(HttpStatus.CONFLICT,"请求号已用于不同报文或账号");
            if(!lease.equals(row.get("lease_token"))){
                if("accepted".equals(row.get("validation_code")))return row;
                if("rejected".equals(row.get("validation_code")))throw new IllegalArgumentException(Objects.toString(row.get("reason")));
                if(time(row.get("lease_until")).isAfter(now))throw new ResponseStatusException(HttpStatus.CONFLICT,"请求正在处理，请稍后用原请求号重试");
                jdbc.update("UPDATE observation_attempt SET lease_token=?,lease_until=? WHERE request_key=?",lease,now.plusSeconds(60),requestKey);
            }
            return null;
        });
        if(old!=null)return Map.of("observationId",number(old,"observation_id"),"status","accepted","source","simulated");
        try{
            long id=tx.execute(s->accept(DeviceReport.parse(packet,now),packet));
            audit.executeWithoutResult(s->jdbc.update("UPDATE observation_attempt SET validation_code='accepted',observation_id=?,reason=NULL WHERE request_key=? AND lease_token=?",id,requestKey,lease));
            return Map.of("observationId",id,"status","accepted","source","simulated");
        }catch(IllegalArgumentException e){
            audit.executeWithoutResult(s->jdbc.update("UPDATE observation_attempt SET validation_code='rejected',reason=? WHERE request_key=? AND lease_token=?",Objects.toString(e.getMessage(),"观测接收失败").substring(0,Math.min(500,Objects.toString(e.getMessage(),"观测接收失败").length())),requestKey,lease));throw e;
        }
    }
    /** Called inside the formal collection transaction; never calls billing or updates its cursor. */
    public long acceptFormal(DeviceReport report,String packet){return tx.execute(s->accept(report,packet));}
    private long accept(DeviceReport report,String packet){
        var rows=jdbc.queryForList("SELECT m.* FROM water_meter m JOIN sys_user u ON u.id=m.user_id WHERE m.meter_no=? AND m.deleted=0 AND m.status IN (0,1) AND u.deleted=0 AND u.status=0 FOR UPDATE",report.meterNo());
        if(rows.isEmpty())throw new IllegalArgumentException("水表不存在、停用或用户已停用");long meter=number(rows.get(0),"id");
        var previous=jdbc.queryForList("SELECT id,packet_hash FROM meter_observation WHERE meter_id=? AND source='simulated' AND reported_at=?",meter,report.timestamp());
        if(!previous.isEmpty()){
            if(!hash(packet).equals(previous.get(0).get("packet_hash")))throw new IllegalArgumentException("相同表号时间的观测内容冲突");
            return number(previous.get(0),"id");
        }
        jdbc.update("INSERT INTO observation_cursor(meter_id,source) VALUES(?,'simulated') ON DUPLICATE KEY UPDATE meter_id=meter_id",meter);
        var cursor=jdbc.queryForMap("SELECT * FROM observation_cursor WHERE meter_id=? AND source='simulated' FOR UPDATE",meter);
        if(cursor.get("last_valid_at")!=null&&!report.timestamp().isAfter(time(cursor.get("last_valid_at"))))throw new IllegalArgumentException("观测时间乱序，未更新游标");
        BigDecimal floor=cursor.get("last_valid_total")==null?new BigDecimal(Objects.toString(rows.get(0).get("current_reading"),"0")):new BigDecimal(cursor.get("last_valid_total").toString());
        if(report.total().compareTo(floor)<0)throw new IllegalArgumentException("累计观测倒退，需人工核查");
        jdbc.update("INSERT INTO meter_observation(meter_id,source,reported_at,flow,total,temperature,valve,alarm,packet_hash,packet) VALUES(?,'simulated',?,?,?,?,?,?,?,?)",meter,report.timestamp(),report.flow(),report.total(),report.temperature(),report.valve(),report.alarm(),hash(packet),packet);
        long id=jdbc.queryForObject("SELECT id FROM meter_observation WHERE meter_id=? AND source='simulated' AND reported_at=?",Long.class,meter,report.timestamp());
        jdbc.update("UPDATE observation_cursor SET last_valid_at=?,last_valid_total=? WHERE meter_id=? AND source='simulated'",report.timestamp(),report.total(),meter);
        var policy=jdbc.queryForMap("SELECT * FROM diagnosis_policy WHERE id=1");
        if(!"off".equals(policy.get("mode"))&&ids(policy.get("meter_ids")).contains(meter))
            jdbc.update("INSERT INTO diagnosis_job(observation_id,policy_version,mode) VALUES(?,?,?)",id,policy.get("version"),policy.get("mode"));
        return id;
    }
    public List<Map<String,Object>> history(long meter,LocalDateTime until){
        return jdbc.queryForList("SELECT id,reported_at AS reportedAt,flow,total,temperature,valve,alarm FROM (SELECT * FROM meter_observation WHERE meter_id=? AND quality_status='valid' AND reported_at<=? AND reported_at>=? ORDER BY reported_at DESC LIMIT 2800) o ORDER BY reported_at",meter,until,until.minusDays(28));
    }
}
