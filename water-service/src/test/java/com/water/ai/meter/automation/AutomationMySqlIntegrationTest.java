package com.water.ai.meter.automation;

import com.water.ai.meter.service.BillService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="BILLING_MYSQL_TESTS",matches="true")
@SpringBootTest(properties={
        "spring.datasource.url=jdbc:mysql://localhost:3308/water_meter_billing_test?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true",
        "spring.datasource.username=root","spring.datasource.password=123456","automation.scheduling.enabled=false",
        "spring.main.web-application-type=none","spring.cloud.nacos.discovery.enabled=false",
        "management.health.elasticsearch.enabled=false","logging.file.name=../logs/automation-tests.log",
        "logging.level.root=WARN","logging.level.com.water.ai.meter=WARN",
        "mybatis-plus.configuration.log-impl=org.apache.ibatis.logging.nologging.NoLoggingImpl"
})
class AutomationMySqlIntegrationTest {
    @Autowired CollectionService collection;
    @Autowired PenaltyService penalties;
    @Autowired BillService billing;
    @Autowired com.water.ai.meter.service.MeterReadingService readingService;
    @Autowired JdbcTemplate jdbc;
    @Autowired org.springframework.transaction.PlatformTransactionManager manager;
    long user,meter; String marker;
    List<Long> plans=new ArrayList<>(), policies=new ArrayList<>();
    @BeforeEach void fixture() {
        assertThat(jdbc.queryForObject("SELECT DATABASE()",String.class)).isEqualTo("water_meter_billing_test");
        marker="AUTOIT_"+UUID.randomUUID().toString().replace("-","");
        jdbc.update("INSERT INTO sys_user(username,password,user_type,deleted) VALUES (?,'test','residential',0)",marker);
        user=jdbc.queryForObject("SELECT id FROM sys_user WHERE username=?",Long.class,marker);
        jdbc.update("INSERT INTO water_meter(meter_no,user_id,current_reading,status,deleted) VALUES (?,?,100,0,0)",marker,user);
        meter=jdbc.queryForObject("SELECT id FROM water_meter WHERE meter_no=?",Long.class,marker);
    }
    @AfterEach void cleanup() {
        for(long plan:plans) {
            jdbc.update("DELETE t FROM automation_task t JOIN automation_run r ON r.id=t.run_id WHERE r.plan_id=?",plan);
            jdbc.update("DELETE FROM automation_run WHERE plan_id=?",plan);
            jdbc.update("DELETE FROM automation_plan_meter WHERE plan_id=?",plan);
            jdbc.update("DELETE FROM automation_plan WHERE id=?",plan);
        }
        jdbc.update("DELETE l FROM penalty_ledger l JOIN bill b ON b.id=l.bill_id WHERE b.user_id=?",user);
        for(long id:policies) jdbc.update("DELETE FROM penalty_policy WHERE id=?",id);
        jdbc.update("DELETE FROM automation_receipt WHERE meter_id=?",meter);
        jdbc.update("DELETE p FROM bill_payment p JOIN bill b ON b.id=p.bill_id WHERE b.user_id=?",user);
        jdbc.update("DELETE FROM bill WHERE user_id=?",user);
        jdbc.update("DELETE FROM anomaly_record WHERE meter_id=?",meter);
        jdbc.update("DELETE FROM meter_reading WHERE meter_id=?",meter);
        jdbc.update("DELETE FROM water_meter WHERE id=?",meter);
        jdbc.update("DELETE FROM sys_user WHERE id=?",user);
    }
    long plan(String scenario,int attempts) {
        long id=collection.createPlan(new CollectionService.PlanInput(marker,List.of(meter),60,true,attempts,5,scenario,new BigDecimal("1.00")));
        plans.add(id); return id;
    }
    String packet(String total) { return marker+"|"+LocalDateTime.now().minusMinutes(1).withNano(0)+"|0.1|"+total+"|25|OPEN|0"; }
    @Test void firstFailureSurvivesRetryAndRepeatedTriggerDoesNotDuplicateBill() {
        long plan=plan("first_failure",3),run=collection.trigger(plan,"same-request");
        assertThat(collection.trigger(plan,"same-request")).isEqualTo(run);
        long task=jdbc.queryForObject("SELECT id FROM automation_task WHERE run_id=?",Long.class,run);
        collection.processTask(task);
        assertThat(jdbc.queryForObject("SELECT status FROM automation_task WHERE id=?",String.class,task)).isEqualTo("retry");
        jdbc.update("UPDATE automation_task SET next_attempt_at=DATE_SUB(NOW(),INTERVAL 1 SECOND) WHERE id=?",task);
        // A new service instance represents a process restart: all recovery state comes from MySQL.
        new CollectionService(jdbc,billing,manager).processTask(task);
        collection.processTask(task);
        assertThat(jdbc.queryForObject("SELECT attempts FROM automation_task WHERE id=?",Integer.class,task)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT status FROM automation_task WHERE id=?",String.class,task)).isEqualTo("success");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bill WHERE meter_id=?",Integer.class,meter)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT usage_amount FROM meter_reading WHERE meter_id=?",BigDecimal.class,meter)).isEqualByComparingTo("1.00");
    }
    @Test void retryLimitRecordsOneDeterministicFailure() {
        long run=collection.trigger(plan("always_failure",2),"failure");
        long task=jdbc.queryForObject("SELECT id FROM automation_task WHERE run_id=?",Long.class,run);
        collection.processTask(task);
        jdbc.update("UPDATE automation_task SET next_attempt_at=DATE_SUB(NOW(),INTERVAL 1 SECOND) WHERE id=?",task);
        collection.processTask(task); collection.processTask(task);
        assertThat(jdbc.queryForObject("SELECT status FROM automation_task WHERE id=?",String.class,task)).isEqualTo("failed");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM anomaly_record WHERE meter_id=? AND anomaly_type='collection_failure'",Integer.class,meter)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bill WHERE meter_id=?",Integer.class,meter)).isZero();
    }
    @Test void concurrentIdenticalPacketsCreateOneReadingAndBill() throws Exception {
        String input=packet("101.25");
        var ids=race(()->collection.ingest(input),()->collection.ingest(input));
        assertThat(ids.get(0)).isEqualTo(ids.get(1));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM meter_reading WHERE meter_id=?",Integer.class,meter)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bill WHERE meter_id=?",Integer.class,meter)).isEqualTo(1);
        assertThatThrownBy(()->collection.ingest(input.replace("101.25","102.25"))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("冲突");
    }
    @Test void regressionAndBillingFailureDoNotPartiallyAdvanceMeter() {
        assertThatThrownBy(()->collection.ingest(packet("99"))).hasMessageContaining("回退");
        jdbc.update("UPDATE sys_user SET user_type='unsupported' WHERE id=?",user);
        assertThatThrownBy(()->collection.ingest(packet("101"))).hasMessageContaining("自动出账失败");
        assertThat(jdbc.queryForObject("SELECT current_reading FROM water_meter WHERE id=?",BigDecimal.class,meter)).isEqualByComparingTo("100");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM meter_reading WHERE meter_id=?",Integer.class,meter)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM automation_receipt WHERE meter_id=?",Integer.class,meter)).isZero();
    }
    @Test void adaptiveScheduleHalvesIntervalForWeakSignalAndPausePreventsAutomaticRun() {
        jdbc.update("UPDATE water_meter SET signal_strength=10 WHERE id=?",meter);
        long plan=plan("normal",2);
        collection.trigger(plan,"adaptive");
        Long minutes=jdbc.queryForObject("SELECT TIMESTAMPDIFF(MINUTE,NOW(),next_run_at) FROM automation_plan WHERE id=?",Long.class,plan);
        assertThat(minutes).isBetween(29L,30L);
        collection.enabled(plan,false);
        jdbc.update("UPDATE automation_plan SET next_run_at=DATE_SUB(NOW(),INTERVAL 1 DAY) WHERE id=?",plan);
        collection.tick();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM automation_run WHERE plan_id=?",Integer.class,plan)).isEqualTo(1);
    }
    long bill() {
        collection.ingest(packet("101"));
        long id=jdbc.queryForObject("SELECT id FROM bill WHERE meter_id=?",Long.class,meter);
        jdbc.update("UPDATE bill SET total_amount=100,water_fee=100,sewage_fee=0,penalty=0,due_date=? WHERE id=?",LocalDate.now().minusDays(5).atStartOfDay(),id);
        return id;
    }
    void policy(LocalDate effective,String rate,String cap,int grace) {
        jdbc.update("INSERT INTO penalty_policy(enabled,grace_days,daily_rate,cap_ratio,effective_from) VALUES (1,?,?,?,?)",grace,new BigDecimal(rate),new BigDecimal(cap),effective);
        policies.add(jdbc.queryForObject("SELECT MAX(id) FROM penalty_policy",Long.class));
    }
    LocalDate ledgerStart(LocalDate start) {
        LocalDate installed=jdbc.queryForObject("SELECT MIN(effective_from) FROM penalty_policy",LocalDate.class);
        LocalDate firstOverdue=start.minusDays(4); // bill() sets due date to five days ago.
        return installed.isAfter(firstOverdue)?installed:firstOverdue;
    }
    @Test void historicalPartialPaymentAndRuleVersionsUseEachDaysActualPrincipal() {
        long id=bill(); LocalDate start=LocalDate.now();
        policy(start,"0.001","0.1",0);
        assertThat(billing.payBill(id,new BigDecimal("40"),"cash",marker).get("success")).isEqualTo(true);
        jdbc.update("UPDATE bill_payment SET paid_time=? WHERE bill_id=?",start.plusDays(1).atTime(12,0),id);
        policy(start.plusDays(1),"0.002","0.1",0);
        assertThat(penalties.accrueBill(id,start.plusDays(2))).isEqualTo((int)java.time.temporal.ChronoUnit.DAYS.between(ledgerStart(start),start.plusDays(2)));
        assertThat(penalties.accrueBill(id,start.plusDays(2))).isZero();
        assertThat(jdbc.queryForObject("SELECT penalty FROM bill WHERE id=?",BigDecimal.class,id)).isEqualByComparingTo("0.22");
        assertThat(jdbc.queryForList("SELECT principal_outstanding FROM penalty_ledger WHERE bill_id=? AND accrual_date>=? ORDER BY accrual_date",BigDecimal.class,id,start)).containsExactly(new BigDecimal("100.00"),new BigDecimal("60.00"));
        assertThat(jdbc.queryForObject("SELECT COALESCE(SUM(amount),0) FROM penalty_ledger WHERE bill_id=? AND accrual_date<?",BigDecimal.class,id,start)).isEqualByComparingTo("0");
    }
    @Test void concurrentAccrualIsUniqueCapsAndNeverReopensSettledBill() throws Exception {
        long id=bill(); LocalDate start=LocalDate.now(); policy(start,"0.05","0.01",0);
        race(()->penalties.accrueBill(id,start.plusDays(2)),()->penalties.accrueBill(id,start.plusDays(2)));
        assertThat(jdbc.queryForObject("SELECT penalty FROM bill WHERE id=?",BigDecimal.class,id)).isEqualByComparingTo("1");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM penalty_ledger WHERE bill_id=?",Integer.class,id)).isEqualTo((int)java.time.temporal.ChronoUnit.DAYS.between(ledgerStart(start),start.plusDays(2)));
        assertThat(billing.payBill(id,new BigDecimal("101"),"cash",marker).get("success")).isEqualTo(true);
        assertThat(penalties.accrueBill(id,start.plusDays(5))).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM bill WHERE id=?",Integer.class,id)).isEqualTo(1);
    }
    @Test void graceAndUpgradeDatePreventRetroactivePenalty() {
        long id=bill(); LocalDate start=LocalDate.now(); policy(start,"0.001","0.1",6);
        penalties.accrueBill(id,start.plusDays(3));
        assertThat(jdbc.queryForObject("SELECT MIN(accrual_date) FROM penalty_ledger WHERE bill_id=?",LocalDate.class,id)).isEqualTo(ledgerStart(start));
        assertThat(jdbc.queryForObject("SELECT penalty FROM bill WHERE id=?",BigDecimal.class,id)).isEqualByComparingTo("0.10");
    }
    @Test void staleReviewCannotRewindTheAutomaticallyBilledBaseline() {
        collection.ingest(packet("101"));
        jdbc.update("INSERT INTO meter_reading(meter_id,meter_no,user_id,reading_value,usage_amount,status,reading_time,reading_type,deleted) VALUES (?,?,?,100.50,0.50,0,?,'ai_image',0)",meter,marker,user,LocalDateTime.now().minusMinutes(2));
        long pending=jdbc.queryForObject("SELECT MAX(id) FROM meter_reading WHERE meter_id=?",Long.class,meter);
        assertThat(readingService.reviewReading(pending,"test",true,null).get("success")).isEqualTo(false);
        assertThat(readingService.manualReading(meter,new BigDecimal("99"),"test").get("success")).isEqualTo(false);
        assertThat(jdbc.queryForObject("SELECT current_reading FROM water_meter WHERE id=?",BigDecimal.class,meter)).isEqualByComparingTo("101");
        String next=marker+"|"+LocalDateTime.now().withNano(0)+"|0.1|102|25|OPEN|0";
        long nextId=collection.ingest(next);
        assertThat(jdbc.queryForObject("SELECT usage_amount FROM meter_reading WHERE id=?",BigDecimal.class,nextId)).isEqualByComparingTo("1");
    }

    @Test void waitingRetriesCannotStarveOtherDuePlans() {
        for(int i=0;i<20;i++) {
            long waiting=plan("always_failure",3);
            collection.trigger(waiting,"waiting");
            jdbc.update("UPDATE automation_plan SET next_run_at=DATE_SUB(NOW(),INTERVAL 1 DAY) WHERE id=?",waiting);
        }
        long ready=plan("normal",3);
        jdbc.update("UPDATE automation_plan SET next_run_at=DATE_SUB(NOW(),INTERVAL 1 MINUTE) WHERE id=?",ready);
        collection.tick();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM automation_run WHERE plan_id=?",Integer.class,ready)).isEqualTo(1);
    }

    @Test void disabledPolicyStillMarksAnUnpaidBillOverdue() {
        long id=bill();
        penalties.accrueBill(id,LocalDate.now());
        assertThat(jdbc.queryForObject("SELECT status FROM bill WHERE id=?",Integer.class,id)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT penalty FROM bill WHERE id=?",BigDecimal.class,id)).isEqualByComparingTo("0");
    }

    @Test void paymentAndPenaltyShareLockWithoutLosingEitherAmount() throws Exception {
        long id=bill();LocalDate start=LocalDate.now();policy(start,"0.001","0.1",0);
        race(()->{penalties.accrueBill(id,start.plusDays(1));return true;},()->billing.payBill(id,new BigDecimal("40"),"cash",marker).get("success"));
        BigDecimal amount=jdbc.queryForObject("SELECT penalty FROM bill WHERE id=?",BigDecimal.class,id);
        assertThat(amount).isIn(new BigDecimal("0.06"),new BigDecimal("0.10"));
        assertThat(jdbc.queryForObject("SELECT paid_amount FROM bill WHERE id=?",BigDecimal.class,id)).isEqualByComparingTo("40");
        assertThat(jdbc.queryForObject("SELECT total_amount FROM bill WHERE id=?",BigDecimal.class,id)).isEqualByComparingTo(new BigDecimal("100").add(amount));
        assertThat(jdbc.queryForObject("SELECT status FROM bill WHERE id=?",Integer.class,id)).isEqualTo(3);
    }

    private <T> List<T> race(Supplier<T> a,Supplier<T> b) throws Exception {
        var pool=Executors.newFixedThreadPool(2);var latch=new CountDownLatch(1);
        try {var x=pool.submit(()->{latch.await();return a.get();});var y=pool.submit(()->{latch.await();return b.get();});latch.countDown();return List.of(x.get(20,TimeUnit.SECONDS),y.get(20,TimeUnit.SECONDS));}
        finally {pool.shutdownNow();pool.awaitTermination(5,TimeUnit.SECONDS);}
    }
}
