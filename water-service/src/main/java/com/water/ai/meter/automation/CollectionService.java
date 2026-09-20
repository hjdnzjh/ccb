package com.water.ai.meter.automation;

import com.water.ai.meter.service.BillService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class CollectionService {
    private final JdbcTemplate jdbc;
    private final BillService billing;
    private final TransactionTemplate tx;
    private com.water.ai.meter.diagnosis.ObservationService observations;
    @org.springframework.beans.factory.annotation.Autowired
    public CollectionService(JdbcTemplate jdbc,BillService billing,PlatformTransactionManager manager,com.water.ai.meter.diagnosis.ObservationService observations){
        this(jdbc,billing,manager);this.observations=observations;
    }
    public CollectionService(JdbcTemplate jdbc, BillService billing, PlatformTransactionManager manager) {
        this.jdbc=jdbc; this.billing=billing; this.tx=new TransactionTemplate(manager);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }
    public record PlanInput(String name, List<Long> meterIds, int intervalMinutes, boolean adaptive,
                            int maxAttempts, int retrySeconds, String scenario, BigDecimal incrementAmount) {}
    public long createPlan(PlanInput input) {
        if (input.name()==null || input.name().isBlank() || input.name().length()>100
                || input.meterIds()==null || input.meterIds().isEmpty() || input.meterIds().size()>200
                || input.meterIds().stream().anyMatch(Objects::isNull)
                || input.intervalMinutes()<1 || input.intervalMinutes()>43200
                || input.maxAttempts()<1 || input.maxAttempts()>5 || input.retrySeconds()<5 || input.retrySeconds()>3600
                || input.scenario()==null || !Set.of("normal","first_failure","always_failure","fault").contains(input.scenario())
                || input.incrementAmount()==null || input.incrementAmount().signum()<0 || input.incrementAmount().scale()>2
                || input.incrementAmount().compareTo(new BigDecimal("100000"))>0)
            throw new IllegalArgumentException("计划参数无效：1至200台表，间隔1至43200分钟，尝试1至5次，重试5至3600秒");
        return tx.execute(s -> {
            for (long id:new LinkedHashSet<>(input.meterIds())) {
                Integer count=jdbc.queryForObject("SELECT COUNT(*) FROM water_meter WHERE id=? AND deleted=0 AND status IN (0,1) AND user_id IS NOT NULL",Integer.class,id);
                if (count==null || count!=1) throw new IllegalArgumentException("水表不存在、已停用或未绑定用户："+id);
            }
            long id=insert("INSERT INTO automation_plan(name,interval_minutes,adaptive,max_attempts,retry_seconds,scenario,increment_amount,next_run_at) VALUES (?,?,?,?,?,?,?,?)",
                    input.name().trim(),input.intervalMinutes(),input.adaptive(),input.maxAttempts(),input.retrySeconds(),input.scenario(),input.incrementAmount(),LocalDateTime.now(java.time.ZoneId.of("Asia/Shanghai")));
            for (long meter:new LinkedHashSet<>(input.meterIds())) jdbc.update("INSERT INTO automation_plan_meter(plan_id,meter_id) VALUES (?,?)",id,meter);
            return id;
        });
    }
    public List<Map<String,Object>> plans() {
        return jdbc.queryForList("SELECT p.*,(SELECT COUNT(*) FROM automation_plan_meter pm WHERE pm.plan_id=p.id) meter_count,EXISTS(SELECT 1 FROM automation_coverage_meter c WHERE c.plan_id=p.id) auto_managed FROM automation_plan p ORDER BY p.id DESC LIMIT 200");
    }
    public List<Map<String,Object>> meters() {
        return jdbc.queryForList("SELECT id,meter_no,status,signal_strength,battery_level FROM water_meter WHERE deleted=0 AND status IN (0,1) AND user_id IS NOT NULL ORDER BY id LIMIT 2000");
    }
    public List<Map<String,Object>> runs() {
        return jdbc.queryForList("SELECT r.*,p.name,COUNT(t.id) total,SUM(t.status='success') succeeded,SUM(t.status='failed') failed,SUM(t.status IN ('pending','retry')) pending FROM automation_run r JOIN automation_plan p ON p.id=r.plan_id LEFT JOIN automation_task t ON t.run_id=r.id GROUP BY r.id,p.name ORDER BY r.id DESC LIMIT 100");
    }
    public List<Map<String,Object>> tasks(long runId) { return jdbc.queryForList("SELECT * FROM automation_task WHERE run_id=? ORDER BY id",runId); }
    public void enabled(long id, boolean enabled) {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM automation_coverage_meter WHERE plan_id=?",Integer.class,id)>0)
            throw new IllegalArgumentException("自主计划由覆盖策略管理；请修改覆盖策略或停用水表档案");
        if (jdbc.update("UPDATE automation_plan SET enabled=? WHERE id=?",enabled,id)!=1) throw new IllegalArgumentException("计划不存在");
    }
    public long trigger(long planId,String requestKey) {
        if (requestKey==null || !requestKey.matches("[A-Za-z0-9_:.T-]{1,100}")) throw new IllegalArgumentException("请提供有效且可重试的请求号");
        return tx.execute(s -> createRun(planId,requestKey,false));
    }
    private long createRun(long id,String key,boolean scheduled) {
        var rows=jdbc.queryForList("SELECT * FROM automation_plan WHERE id=? FOR UPDATE",id);
        if (rows.isEmpty()) throw new IllegalArgumentException("计划不存在");
        var plan=rows.get(0);
        var previous=jdbc.queryForList("SELECT id FROM automation_run WHERE plan_id=? AND request_key=?",id,key);
        if (!previous.isEmpty()) return number(previous.get(0),"id");
        if (scheduled && (!flag(plan.get("enabled")) || time(plan.get("next_run_at")).isAfter(LocalDateTime.now(java.time.ZoneId.of("Asia/Shanghai"))))) return 0;
        Long pending=jdbc.queryForObject("SELECT COUNT(*) FROM automation_task t JOIN automation_run r ON r.id=t.run_id WHERE r.plan_id=? AND t.status IN ('pending','retry')",Long.class,id);
        if (pending!=null && pending>0) { if (scheduled) return 0; throw new IllegalArgumentException("计划仍有待采集或补抄任务，请等待结束"); }
        var meters=jdbc.queryForList("SELECT m.* FROM water_meter m JOIN automation_plan_meter pm ON pm.meter_id=m.id WHERE pm.plan_id=? AND m.deleted=0 ORDER BY m.id",id);
        long run=insert("INSERT INTO automation_run(plan_id,request_key) VALUES (?,?)",id,key);
        boolean unhealthy=false;
        for (var meter:meters) {
            unhealthy |= number(meter,"status")!=0 || number(meter,"signal_strength")<30 || number(meter,"battery_level")<20;
            LocalDateTime reportTime=LocalDateTime.now(java.time.ZoneId.of("Asia/Shanghai")).withNano(0);
            if (meter.get("last_reading_time")!=null && !reportTime.isAfter(time(meter.get("last_reading_time"))))
                reportTime=time(meter.get("last_reading_time")).plusSeconds(1).withNano(0);
            BigDecimal total=decimal(meter,"current_reading").add(decimal(plan,"increment_amount"));
            boolean fault="fault".equals(plan.get("scenario"));
            String packet=meter.get("meter_no")+"|"+reportTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)+"|"+(fault?"12":"0.1")+"|"+total.toPlainString()+"|25|OPEN|"+(fault?"E01":"0");
            insert("INSERT INTO automation_task(run_id,meter_id,packet,scenario,max_attempts,retry_seconds,next_attempt_at) VALUES (?,?,?,?,?,?,?)",
                    run,meter.get("id"),packet,plan.get("scenario"),plan.get("max_attempts"),plan.get("retry_seconds"),LocalDateTime.now(java.time.ZoneId.of("Asia/Shanghai")).withNano(0));
        }
        long minutes=number(plan,"interval_minutes");
        if (flag(plan.get("adaptive")) && unhealthy) minutes=Math.max(1,minutes/2);
        jdbc.update("UPDATE automation_plan SET next_run_at=? WHERE id=?",LocalDateTime.now(java.time.ZoneId.of("Asia/Shanghai")).plusMinutes(minutes),id);
        return run;
    }
    public void tick() {
        for (var plan:jdbc.queryForList("SELECT p.id,p.next_run_at FROM automation_plan p WHERE p.enabled=1 AND p.next_run_at<=NOW() " +
                "AND NOT EXISTS (SELECT 1 FROM automation_run r JOIN automation_task t ON t.run_id=r.id WHERE r.plan_id=p.id AND t.status IN ('pending','retry')) " +
                "ORDER BY p.next_run_at,p.id LIMIT 20")) {
            try { tx.execute(s -> createRun(number(plan,"id"),"auto:"+time(plan.get("next_run_at")).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),true)); }
            catch (RuntimeException e) { org.slf4j.LoggerFactory.getLogger(getClass()).warn("计划 {} 暂时无法运行",plan.get("id"),e); }
        }
        processDueTasks();
    }
    public int processDueTasks() {
        var tasks=jdbc.queryForList("SELECT id FROM automation_task WHERE status IN ('pending','retry') AND next_attempt_at<=NOW() ORDER BY next_attempt_at,id LIMIT 100");
        for (var task:tasks) processTask(number(task,"id"));
        return tasks.size();
    }
    public void processTask(long id) {
        try {
            tx.execute(s -> {
                var task=jdbc.queryForMap("SELECT * FROM automation_task WHERE id=? FOR UPDATE",id);
                if (!Set.of("pending","retry").contains(task.get("status")) || time(task.get("next_attempt_at")).isAfter(LocalDateTime.now(java.time.ZoneId.of("Asia/Shanghai")))) return null;
                if ("always_failure".equals(task.get("scenario")) || ("first_failure".equals(task.get("scenario")) && number(task,"attempts")==0))
                    throw new IllegalArgumentException("模拟通信超时（非真实设备）");
                long reading=accept((String)task.get("packet"),number(task,"meter_id"));
                jdbc.update("UPDATE automation_task SET status='success',attempts=attempts+1,reading_id=?,last_error=NULL,completed_at=NOW() WHERE id=?",reading,id);
                return null;
            });
        } catch (RuntimeException failure) {
            tx.execute(s -> {
                var task=jdbc.queryForMap("SELECT * FROM automation_task WHERE id=? FOR UPDATE",id);
                if (!Set.of("pending","retry").contains(task.get("status")) || time(task.get("next_attempt_at")).isAfter(LocalDateTime.now(java.time.ZoneId.of("Asia/Shanghai")))) return null;
                long attempts=number(task,"attempts")+1;
                boolean exhausted=attempts>=number(task,"max_attempts");
                String message=Objects.toString(failure.getMessage(),"采集失败");
                jdbc.update("UPDATE automation_task SET status=?,attempts=?,last_error=?,next_attempt_at=?,completed_at=? WHERE id=?",
                        exhausted?"failed":"retry",attempts,message.substring(0,Math.min(500,message.length())),
                        LocalDateTime.now(java.time.ZoneId.of("Asia/Shanghai")).plusSeconds(number(task,"retry_seconds")),exhausted?LocalDateTime.now(java.time.ZoneId.of("Asia/Shanghai")):null,id);
                if (exhausted) {
                    var meters=jdbc.queryForList("SELECT * FROM water_meter WHERE id=?",task.get("meter_id"));
                    if (!meters.isEmpty()) alert(meters.get(0),new DeviceReport.Alert("collection_failure","high","模拟采集达到最大尝试次数；任务 "+id),Objects.toString(task.get("packet")));
                }
                return null;
            });
        }
    }
    public long ingest(String packet) { return tx.execute(s -> accept(packet,null)); }
    private long accept(String packet,Long expectedMeter) {
        DeviceReport report=DeviceReport.parse(packet,LocalDateTime.now(java.time.ZoneId.of("Asia/Shanghai")));
        var meters=jdbc.queryForList("SELECT * FROM water_meter WHERE meter_no=? AND deleted=0 FOR UPDATE",report.meterNo());
        if (meters.isEmpty()) throw new IllegalArgumentException("报文表号不存在");
        var meter=meters.get(0); long meterId=number(meter,"id");
        if (expectedMeter!=null && expectedMeter!=meterId) throw new IllegalArgumentException("报文表号与任务不匹配");
        var receipts=jdbc.queryForList("SELECT * FROM automation_receipt WHERE meter_id=? AND reported_at=?",meterId,report.timestamp());
        if (!receipts.isEmpty()) {
            if (!packet.equals(receipts.get(0).get("packet"))) throw new IllegalArgumentException("相同表号时间的报文内容冲突");
            return number(receipts.get(0),"reading_id");
        }
        if (number(meter,"status")>1 || meter.get("user_id")==null) throw new IllegalArgumentException("水表已停用或未绑定用户");
        if (report.total().compareTo(decimal(meter,"current_reading"))<0) throw new IllegalArgumentException("累计读数回退，需人工核查");
        if (meter.get("last_reading_time")!=null && !report.timestamp().isAfter(time(meter.get("last_reading_time")))) throw new IllegalArgumentException("报文时间早于或等于已采集时间");
        var alerts=report.anomalies();
        if(observations!=null)observations.acceptFormal(report,packet);
        long reading=insert("INSERT INTO meter_reading(meter_id,meter_no,user_id,reading_value,usage_amount,reading_type,status,reviewer,review_time,reading_time,reading_period,anomaly_flag,remark,deleted) VALUES (?,?,?,?,?,'simulated',1,'automation',NOW(),?,?,?, '七字段模拟设备；规则校验自动确认',0)",
                meterId,report.meterNo(),meter.get("user_id"),report.total(),report.total().subtract(decimal(meter,"current_reading")),report.timestamp(),report.timestamp().format(DateTimeFormatter.ofPattern("yyyy-MM")),alerts.isEmpty()?0:1);
        jdbc.update("INSERT INTO automation_receipt(meter_id,reported_at,packet,reading_id) VALUES (?,?,?,?)",meterId,report.timestamp(),packet,reading);
        jdbc.update("UPDATE water_meter SET last_reading=current_reading,current_reading=?,last_reading_time=? WHERE id=?",report.total(),report.timestamp(),meterId);
        for (var a:alerts) alert(meter,a,packet);
        var result=billing.generateBill(meterId,reading);
        if (!Boolean.TRUE.equals(result.get("success"))) throw new IllegalArgumentException("自动出账失败："+result.get("message"));
        return reading;
    }
    private void alert(Map<String,Object> meter,DeviceReport.Alert alert,String packet) {
        jdbc.update("INSERT INTO anomaly_record(anomaly_no,meter_id,user_id,anomaly_type,severity,description,detection_detail,status,detected_time,remark,deleted) VALUES (?,?,?,?,?,?,?,0,NOW(),'确定性规则；模拟设备',0)",
                "AUTO-"+UUID.randomUUID().toString().replace("-",""),meter.get("id"),meter.get("user_id"),alert.type(),alert.severity(),alert.description(),packet);
    }
    private long insert(String sql,Object... values) {
        GeneratedKeyHolder key=new GeneratedKeyHolder();
        jdbc.update(connection -> { var ps=connection.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS); for(int i=0;i<values.length;i++) ps.setObject(i+1,values[i]); return ps; },key);
        return Objects.requireNonNull(key.getKey()).longValue();
    }
    static long number(Map<String,Object> row,String key) { Object v=row.get(key); return v==null?0:((Number)v).longValue(); }
    static BigDecimal decimal(Map<String,Object> row,String key) { Object v=row.get(key); return v==null?BigDecimal.ZERO:new BigDecimal(v.toString()); }
    static boolean flag(Object value) { return value instanceof Boolean b?b:value instanceof Number n && n.intValue()!=0; }
    static LocalDateTime time(Object value) { return value instanceof Timestamp t?t.toLocalDateTime():(LocalDateTime)value; }
}
