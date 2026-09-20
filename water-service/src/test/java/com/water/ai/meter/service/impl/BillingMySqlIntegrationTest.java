package com.water.ai.meter.service.impl;

import com.water.ai.meter.service.BillService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import static org.assertj.core.api.Assertions.*;

/** Explicit opt-in. Never connects to the development water_meter_db database. */
@EnabledIfEnvironmentVariable(named = "BILLING_MYSQL_TESTS", matches = "true")
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:mysql://localhost:3308/water_meter_billing_test?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true",
        "spring.datasource.username=root", "spring.datasource.password=123456",
        "automation.scheduling.enabled=false",
        "spring.main.web-application-type=none", "spring.cloud.nacos.discovery.enabled=false",
        "management.health.elasticsearch.enabled=false", "logging.file.name=../logs/billing-tests.log",
        "logging.level.root=WARN", "logging.level.com.water.ai.meter=WARN",
        "mybatis-plus.configuration.log-impl=org.apache.ibatis.logging.nologging.NoLoggingImpl"
})
class BillingMySqlIntegrationTest {
    @Autowired BillService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
    long userId, meterId, readingId;
    String marker;

    @BeforeEach
    void fixture() {
        assertThat(jdbc.queryForObject("SELECT DATABASE()",String.class)).isEqualTo("water_meter_billing_test");
        marker = "IT_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.update("INSERT INTO sys_user(username,password,user_type,deleted) VALUES (?, 'test','residential',0)", marker);
        userId = jdbc.queryForObject("SELECT id FROM sys_user WHERE username=?",Long.class,marker);
        jdbc.update("INSERT INTO water_meter(meter_no,user_id,meter_type,deleted) VALUES (?,?,'digital',0)",marker,userId);
        meterId = jdbc.queryForObject("SELECT id FROM water_meter WHERE meter_no=?",Long.class,marker);
        jdbc.update("INSERT INTO meter_reading(meter_id,meter_no,user_id,reading_value,usage_amount,reading_type,status,reading_time,reading_period,deleted) " +
                "VALUES (?,?,?,150,30,'manual',1,NOW(),'2026-08',0)",meterId,marker,userId);
        readingId=jdbc.queryForObject("SELECT id FROM meter_reading WHERE meter_id=?",Long.class,meterId);
    }

    @AfterEach
    void cleanup() {
        jdbc.execute("DROP TRIGGER IF EXISTS billing_it_fail_update");
        jdbc.update("DELETE p FROM bill_payment p INNER JOIN bill b ON p.bill_id=b.id WHERE b.user_id=?",userId);
        jdbc.update("DELETE FROM bill WHERE user_id=?",userId);
        jdbc.update("DELETE FROM meter_reading WHERE meter_id=?",meterId);
        jdbc.update("DELETE FROM water_meter WHERE id=?",meterId);
        jdbc.update("DELETE FROM sys_user WHERE id=?",userId);
    }

    long bill() {
        var result=service.generateBill(meterId,readingId);
        assertThat(result.get("success")).isEqualTo(true);
        long id=((Number)((Map<?,?>)result.get("data")).get("billId")).longValue();
        // Use a round amount for payment state tests; generation calculation is tested separately.
        jdbc.update("UPDATE bill SET total_amount=100,water_fee=100,sewage_fee=0 WHERE id=?",id);
        return id;
    }

    Map<String,Object> pay(long id,String amount,String key) {
        return service.payBill(id,new BigDecimal(amount),"cash",key);
    }

    @Test
    void concurrentGenerationCreatesOneBillAndValidFeeJson() throws Exception {
        var results=race(() -> service.generateBill(meterId,readingId), () -> service.generateBill(meterId,readingId));
        assertThat(results).allSatisfy(r -> assertThat(r.get("success")).isEqualTo(true));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bill WHERE reading_id=?",Integer.class,readingId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT total_amount FROM bill WHERE reading_id=?",BigDecimal.class,readingId))
                .isEqualByComparingTo("117.99");
        assertThat(jdbc.queryForObject("SELECT JSON_VALID(ladder_detail) FROM bill WHERE reading_id=?",Integer.class,readingId)).isEqualTo(1);
    }

    @Test
    void generationSeesCommittedBillEvenWithEarlierTransactionSnapshot() throws Exception {
        ExecutorService executor=Executors.newSingleThreadExecutor();
        try {
            new org.springframework.transaction.support.TransactionTemplate(transactionManager).execute(status -> {
                // Reproduce the pre-lock read in batch generation under MySQL REPEATABLE READ.
                jdbc.queryForObject("SELECT id FROM meter_reading WHERE id=?",Long.class,readingId);
                try {
                    assertThat(executor.submit(() -> service.generateBill(meterId,readingId)).get(15,TimeUnit.SECONDS).get("success")).isEqualTo(true);
                } catch (Exception e) { throw new RuntimeException(e); }
                var result=service.generateBill(meterId,readingId);
                assertThat(((Map<?,?>)result.get("data")).get("existing")).isEqualTo(true);
                return null;
            });
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bill WHERE reading_id=?",Integer.class,readingId)).isEqualTo(1);
        } finally { executor.shutdownNow(); executor.awaitTermination(5,TimeUnit.SECONDS); }
    }

    @Test
    void concurrentBatchGenerationCreatesOnlyOneBill() throws Exception {
        var results=race(() -> service.batchGenerateBills(List.of(readingId)), () -> service.batchGenerateBills(List.of(readingId)));
        int created=results.stream().map(r -> (Map<?,?>)r.get("data")).mapToInt(r -> ((Number)r.get("created")).intValue()).sum();
        int existing=results.stream().map(r -> (Map<?,?>)r.get("data")).mapToInt(r -> ((Number)r.get("existing")).intValue()).sum();
        assertThat(created).isEqualTo(1);
        assertThat(existing).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bill WHERE reading_id=?",Integer.class,readingId)).isEqualTo(1);
    }

    @Test
    void twoPaymentsAccumulateAndRetriesReuseOriginalReceipt() {
        long id=bill();
        assertThat(pay(id,"40",marker+"_a").get("success")).isEqualTo(true);
        assertThat(jdbc.queryForObject("SELECT status FROM bill WHERE id=?",Integer.class,id)).isEqualTo(3);
        assertThat(pay(id,"60",marker+"_b").get("success")).isEqualTo(true);
        var replay=pay(id,"40",marker+"_a");
        assertThat(((Map<?,?>)replay.get("data")).get("replayed")).isEqualTo(true);
        assertThat(jdbc.queryForObject("SELECT status FROM bill WHERE id=?",Integer.class,id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT SUM(amount) FROM bill_payment WHERE bill_id=?",BigDecimal.class,id))
                .isEqualByComparingTo("100");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bill_payment WHERE bill_id=?",Integer.class,id)).isEqualTo(2);
    }

    @Test
    void concurrentPaymentsCannotOvercollect() throws Exception {
        long id=bill();
        var results=race(() -> pay(id,"70",marker+"_a"), () -> pay(id,"70",marker+"_b"));
        assertThat(results.stream().filter(r -> Boolean.TRUE.equals(r.get("success"))).count()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT paid_amount FROM bill WHERE id=?",BigDecimal.class,id)).isEqualByComparingTo("70");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bill_payment WHERE bill_id=?",Integer.class,id)).isEqualTo(1);
    }

    @Test
    void concurrentIdenticalRequestIsAppliedOnlyOnce() throws Exception {
        long id=bill();
        var results=race(() -> pay(id,"40",marker), () -> pay(id,"40",marker));
        assertThat(results).allSatisfy(r -> assertThat(r.get("success")).isEqualTo(true));
        assertThat(jdbc.queryForObject("SELECT paid_amount FROM bill WHERE id=?",BigDecimal.class,id)).isEqualByComparingTo("40");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bill_payment WHERE bill_id=?",Integer.class,id)).isEqualTo(1);
    }

    @Test
    void updateFailureRollsBackPreviouslyInsertedReceipt() {
        long id=bill();
        jdbc.execute("CREATE TRIGGER billing_it_fail_update BEFORE UPDATE ON bill FOR EACH ROW " +
                "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'intentional billing rollback test'");
        assertThatThrownBy(() -> pay(id,"40",marker)).isInstanceOf(RuntimeException.class);
        assertThat(jdbc.queryForObject("SELECT paid_amount FROM bill WHERE id=?",BigDecimal.class,id)).isEqualByComparingTo("0");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bill_payment WHERE bill_id=?",Integer.class,id)).isZero();
    }

    @Test
    void duplicateKeyAcrossBillsDoesNotAlterTheOtherBill() {
        long id=bill();
        jdbc.update("INSERT INTO bill(bill_no,user_id,total_amount,paid_amount,status,bill_period) VALUES (?,?,100,0,0,'2026-08')",
                marker+"_other",userId);
        long other=jdbc.queryForObject("SELECT id FROM bill WHERE bill_no=?",Long.class,marker+"_other");
        assertThat(pay(id,"40",marker).get("success")).isEqualTo(true);
        assertThat(pay(other,"40",marker).get("success")).isEqualTo(false);
        assertThat(jdbc.queryForObject("SELECT paid_amount FROM bill WHERE id=?",BigDecimal.class,other)).isEqualByComparingTo("0");
    }

    @Test
    void overduePartialBillRemainsInUnpaidAndOverdueQueries() {
        long id=bill();
        jdbc.update("UPDATE bill SET due_date=DATE_SUB(NOW(), INTERVAL 1 DAY),status=2 WHERE id=?",id);
        pay(id,"40",marker);
        assertThat(service.getUnpaidBills(userId)).extracting("id").contains(id);
        assertThat(service.getOverdueBills()).extracting("id").contains(id);
    }

    private List<Map<String,Object>> race(Supplier<Map<String,Object>> first, Supplier<Map<String,Object>> second) throws Exception {
        ExecutorService executor=Executors.newFixedThreadPool(2);
        CountDownLatch start=new CountDownLatch(1);
        try {
            Future<Map<String,Object>> a=executor.submit(() -> { start.await(); return first.get(); });
            Future<Map<String,Object>> b=executor.submit(() -> { start.await(); return second.get(); });
            start.countDown();
            return List.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS));
        } finally { executor.shutdownNow(); executor.awaitTermination(5,TimeUnit.SECONDS); }
    }
}
