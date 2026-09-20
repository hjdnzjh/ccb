package com.water.ai.meter.tariff;

import com.water.ai.meter.service.BillService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="BILLING_MYSQL_TESTS",matches="true")
@SpringBootTest(properties={"spring.datasource.url=jdbc:mysql://localhost:3308/water_meter_billing_test?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true","spring.datasource.username=root","spring.datasource.password=123456","spring.main.web-application-type=none","spring.cloud.nacos.discovery.enabled=false","automation.scheduling.enabled=false","logging.level.root=WARN","logging.level.com.water.ai.meter=WARN","logging.file.name=../logs/tariff-tests.log","mybatis-plus.configuration.log-impl=org.apache.ibatis.logging.nologging.NoLoggingImpl"})
class ResidentialTariffMySqlIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired ResidentialTariffService tariffs;
    @Autowired BillService bills;
    String key;long user,meter,second;Long account;
    LocalDate today=LocalDate.now(ZoneId.of("Asia/Shanghai"));
    LocalDateTime at=LocalDateTime.now(ZoneId.of("Asia/Shanghai")).minusMinutes(10).withNano(0);
    @BeforeEach void setup(){
        assertThat(jdbc.queryForObject("SELECT DATABASE()",String.class)).isEqualTo("water_meter_billing_test");
        key="TARIFF_"+UUID.randomUUID().toString().replace("-","");
        jdbc.update("INSERT INTO sys_user(username,password,user_type) VALUES(?,'test','residential')",key);user=jdbc.queryForObject("SELECT id FROM sys_user WHERE username=?",Long.class,key);
        jdbc.update("INSERT INTO water_meter(meter_no,user_id) VALUES(?,?),(?,?)",key,user,key+"B",user);
        meter=jdbc.queryForObject("SELECT id FROM water_meter WHERE meter_no=?",Long.class,key);second=jdbc.queryForObject("SELECT id FROM water_meter WHERE meter_no=?",Long.class,key+"B");
    }
    @AfterEach void cleanup(){
        jdbc.execute("DROP TRIGGER IF EXISTS tariff_test_fail_snapshot");
        jdbc.update("DELETE s FROM tariff_bill_snapshot s JOIN bill b ON b.id=s.bill_id WHERE b.user_id=?",user);
        jdbc.update("DELETE y FROM tariff_year_balance y JOIN tariff_account a ON a.id=y.account_id WHERE a.user_id=?",user);
        jdbc.update("DELETE am FROM tariff_account_meter am JOIN tariff_account a ON a.id=am.account_id WHERE a.user_id=?",user);
        jdbc.update("DELETE FROM tariff_account WHERE user_id=?",user);
        jdbc.update("DELETE FROM bill WHERE user_id=?",user);
        jdbc.update("DELETE FROM meter_reading WHERE user_id=?",user);
        jdbc.update("DELETE FROM tariff_meter_guard WHERE meter_id IN (?,?)",meter,second);
        jdbc.update("DELETE FROM water_meter WHERE user_id=?",user);jdbc.update("DELETE FROM sys_user WHERE id=?",user);
    }
    void bind(String profile,String opening){account=tariffs.create(new ResidentialTariffService.AccountInput(key,List.of(meter,second),profile,today.withDayOfYear(1),new BigDecimal(opening),"独立测试的已核实期初量"));}
    long reading(long m,String usage,LocalDateTime time){
        jdbc.update("INSERT INTO meter_reading(meter_id,user_id,reading_value,usage_amount,status,reading_time,reading_period) VALUES(?,?,1000,?,1,?,?)",m,user,new BigDecimal(usage),time,time.toString().substring(0,7));
        return jdbc.queryForObject("SELECT MAX(id) FROM meter_reading WHERE meter_id=?",Long.class,m);
    }
    BigDecimal used(){return jdbc.queryForObject("SELECT used_amount FROM tariff_year_balance WHERE account_id=? AND billing_year=?",BigDecimal.class,account,today.getYear());}
    @Test void crossedTierHasSnapshotAndDuplicateBillDoesNotAdvanceAllowance(){
        bind("HZ_RES_V1","210");long r=reading(meter,"20",at);
        assertThat(bills.generateBill(meter,r).get("success")).isEqualTo(true);
        assertThat(bills.generateBill(meter,r).get("success")).isEqualTo(true);
        assertThat(used()).isEqualByComparingTo("230");
        assertThat(jdbc.queryForObject("SELECT total_amount FROM bill WHERE reading_id=?",BigDecimal.class,r)).isEqualByComparingTo("71.30");
        assertThat(jdbc.queryForObject("SELECT ladder_detail FROM bill WHERE reading_id=?",String.class,r)).contains("residential-annual-v1","HZ_RES_V1","210");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tariff_bill_snapshot WHERE account_id=?",Integer.class,account)).isEqualTo(1);
    }
    @Test void differentMetersShareAccountAllowanceUnderConcurrency() throws Exception {
        bind("HZ_RES_V1","210");long r=reading(meter,"10",at),s=reading(second,"10",at);
        var results=race(()->bills.generateBill(meter,r),()->bills.generateBill(second,s));
        for(var result:results)assertThat(result.get("success")).isEqualTo(true);
        assertThat(used()).isEqualByComparingTo("230");
        assertThat(jdbc.queryForObject("SELECT SUM(total_amount) FROM bill WHERE user_id=?",BigDecimal.class,user)).isEqualByComparingTo("71.30");
    }
    @Test void concurrentSameReadingOnlyConsumesOnce() throws Exception {
        bind("YH_RES_V1","0");long r=reading(meter,"20",at);
        for(var result:race(()->bills.generateBill(meter,r),()->bills.generateBill(meter,r)))assertThat(result.get("success")).isEqualTo(true);
        assertThat(used()).isEqualByComparingTo("20");
        assertThat(jdbc.queryForObject("SELECT SUM(total_amount) FROM bill WHERE user_id=?",BigDecimal.class,user)).isEqualByComparingTo("57");
    }
    @RepeatedTest(3) void distinctReadingsOfSameMeterSerializeWithoutLockUpgrade() throws Exception {
        bind("HZ_RES_V1","210");long r=reading(meter,"10",at),s=reading(meter,"10",at);
        for(var result:race(()->bills.generateBill(meter,r),()->bills.generateBill(meter,s)))assertThat(result.get("success")).isEqualTo(true);
        assertThat(used()).isEqualByComparingTo("230");
        assertThat(jdbc.queryForObject("SELECT SUM(total_amount) FROM bill WHERE user_id=?",BigDecimal.class,user)).isEqualByComparingTo("71.30");
    }
    @Test void snapshotFailureRollsBackBillAndAnnualBalance(){
        bind("HZ_RES_V1","210");long r=reading(meter,"20",at);
        jdbc.execute("CREATE TRIGGER tariff_test_fail_snapshot BEFORE INSERT ON tariff_bill_snapshot FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='forced snapshot failure'");
        assertThatThrownBy(()->bills.generateBill(meter,r)).isInstanceOf(RuntimeException.class);
        assertThat(used()).isEqualByComparingTo("210");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bill WHERE reading_id=?",Integer.class,r)).isZero();
    }
    @Test void lateReadingAndMismatchedPeriodDoNotConsumeAllowance(){
        bind("HZ_RES_V1","0");long r=reading(meter,"10",at);bills.generateBill(meter,r);
        long late=reading(second,"10",at.minusMinutes(1));
        assertThatThrownBy(()->bills.generateBill(second,late)).hasMessageContaining("倒序");
        long mismatch=reading(second,"10",at.plusMinutes(1));jdbc.update("UPDATE meter_reading SET reading_period='1900-01' WHERE id=?",mismatch);
        assertThatThrownBy(()->bills.generateBill(second,mismatch)).hasMessageContaining("账期");
        assertThat(used()).isEqualByComparingTo("10");
    }
    @Test void crossYearNeedsBoundaryAndNewYearStartsAtZero(){
        bind("HZ_RES_V1","210");
        jdbc.update("UPDATE tariff_account SET effective_from=? WHERE id=?",today.withDayOfYear(1).minusYears(1),account);
        jdbc.update("UPDATE tariff_year_balance SET billing_year=? WHERE account_id=?",today.getYear()-1,account);
        LocalDateTime boundary=today.withDayOfYear(1).atStartOfDay().minusSeconds(1);
        long prior=reading(meter,"1",boundary.minusDays(5));long current=reading(meter,"20",today.withDayOfYear(1).atTime(1,0));
        assertThatThrownBy(()->bills.generateBill(meter,current)).hasMessageContaining("跨年");
        jdbc.update("UPDATE meter_reading SET reading_time=? WHERE id=?",boundary,prior);
        assertThat(bills.generateBill(meter,current).get("success")).isEqualTo(true);
        assertThat(used()).isEqualByComparingTo("20");
        assertThat(jdbc.queryForObject("SELECT total_amount FROM bill WHERE reading_id=?",BigDecimal.class,current)).isEqualByComparingTo("58");
    }
    @Test void existingBillsCannotBeSilentlyRepricedByBinding(){
        long r=reading(meter,"20",at);bills.generateBill(meter,r);
        BigDecimal old=jdbc.queryForObject("SELECT total_amount FROM bill WHERE reading_id=?",BigDecimal.class,r);
        assertThatThrownBy(()->bind("HZ_RES_V1","0")).hasMessageContaining("已有账单");
        assertThat(jdbc.queryForObject("SELECT total_amount FROM bill WHERE reading_id=?",BigDecimal.class,r)).isEqualByComparingTo(old);
    }
    @Test void repeatedBindingAndWrongOwnerAreRejected(){
        bind("YH_WEST_RES_V1","0");
        assertThatThrownBy(()->bind("HZ_RES_V1","0")).hasMessageContaining("重复绑定");
        jdbc.update("UPDATE sys_user SET user_type='commercial' WHERE id=?",user);
        long r=reading(meter,"10",at);
        assertThatThrownBy(()->bills.generateBill(meter,r)).hasMessageContaining("性质");
        assertThat(used()).isEqualByComparingTo("0");
    }
    private <T> List<T> race(Supplier<T> a,Supplier<T> b) throws Exception {
        var pool=Executors.newFixedThreadPool(2);var latch=new CountDownLatch(1);
        try{var x=pool.submit(()->{latch.await();return a.get();});var y=pool.submit(()->{latch.await();return b.get();});latch.countDown();return List.of(x.get(20,TimeUnit.SECONDS),y.get(20,TimeUnit.SECONDS));}
        finally{pool.shutdownNow();pool.awaitTermination(5,TimeUnit.SECONDS);}
    }
}
