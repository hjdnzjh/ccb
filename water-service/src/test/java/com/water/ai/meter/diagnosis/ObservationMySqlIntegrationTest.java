package com.water.ai.meter.diagnosis;

import com.water.ai.meter.automation.CollectionService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="BILLING_MYSQL_TESTS",matches="true")
@SpringBootTest(properties={"spring.datasource.url=jdbc:mysql://localhost:3308/water_meter_billing_test?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true","spring.datasource.username=root","spring.datasource.password=123456","spring.main.web-application-type=none","spring.cloud.nacos.discovery.enabled=false","automation.scheduling.enabled=false","diagnosis.scheduling.enabled=false","logging.level.root=WARN","logging.level.com.water.ai.meter=WARN","logging.file.name=../logs/innovation-tests.log","mybatis-plus.configuration.log-impl=org.apache.ibatis.logging.nologging.NoLoggingImpl"})
class ObservationMySqlIntegrationTest {
    @Autowired JdbcTemplate jdbc; @Autowired ObservationService observations; @Autowired CollectionService collection;
    long user,meter;String key;LocalDateTime at;
    @BeforeEach void setup(){
        assertThat(jdbc.queryForObject("SELECT DATABASE()",String.class)).isEqualTo("water_meter_billing_test");
        key="OBS_"+UUID.randomUUID().toString().replace("-","");at=LocalDateTime.now(ZoneId.of("Asia/Shanghai")).minusHours(1).withNano(0);
        jdbc.update("INSERT INTO sys_user(username,password) VALUES(?,'test')",key);user=jdbc.queryForObject("SELECT id FROM sys_user WHERE username=?",Long.class,key);
        jdbc.update("INSERT INTO water_meter(meter_no,user_id,current_reading,last_reading_time) VALUES(?,?,100,?)",key,user,at.minusHours(1));
        meter=jdbc.queryForObject("SELECT id FROM water_meter WHERE meter_no=?",Long.class,key);
    }
    @AfterEach void cleanup(){
        jdbc.update("DELETE j FROM diagnosis_job j JOIN meter_observation o ON o.id=j.observation_id WHERE o.meter_id=?",meter);
        jdbc.update("DELETE FROM observation_attempt WHERE meter_id=?",meter);jdbc.update("DELETE FROM observation_cursor WHERE meter_id=?",meter);
        jdbc.update("DELETE FROM meter_observation WHERE meter_id=?",meter);
        jdbc.update("DELETE FROM bill WHERE user_id=?",user);jdbc.update("DELETE FROM tariff_meter_guard WHERE meter_id=?",meter);
        jdbc.update("DELETE FROM automation_receipt WHERE meter_id=?",meter);jdbc.update("DELETE FROM meter_reading WHERE meter_id=?",meter);
        jdbc.update("DELETE FROM water_meter WHERE id=?",meter);jdbc.update("DELETE FROM sys_user WHERE id=?",user);
    }
    String packet(int minutes,String total){return key+"|"+at.plusMinutes(minutes)+"|0.12|"+total+"|25|OPEN|0";}
    @Test void hundredDiagnosticObservationsLeaveFinancialTablesUnchanged(){at=at.minusHours(3);var before=jdbc.queryForMap("SELECT (SELECT COUNT(*) FROM bill) bills,(SELECT COUNT(*) FROM bill_payment) payments,(SELECT COUNT(*) FROM meter_reading) readings,(SELECT COUNT(*) FROM tariff_year_balance) balances");for(int i=0;i<100;i++)observations.ingest(packet(i,String.valueOf(101+i)),key+i,user);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM meter_observation WHERE meter_id=?",Integer.class,meter)).isEqualTo(100);assertThat(jdbc.queryForObject("SELECT current_reading FROM water_meter WHERE id=?",java.math.BigDecimal.class,meter)).isEqualByComparingTo("100");assertThat(jdbc.queryForMap("SELECT (SELECT COUNT(*) FROM bill) bills,(SELECT COUNT(*) FROM bill_payment) payments,(SELECT COUNT(*) FROM meter_reading) readings,(SELECT COUNT(*) FROM tariff_year_balance) balances")).isEqualTo(before);}
    @Test void concurrentDuplicatesHaveOneMeasurement() throws Exception {
        var pool=java.util.concurrent.Executors.newFixedThreadPool(10);
        try{
            var gate=new java.util.concurrent.CountDownLatch(1);var futures=new ArrayList<java.util.concurrent.Future<Map<String,Object>>>();
            for(int i=0;i<10;i++){String request=key+i;futures.add(pool.submit(()->{gate.await();return observations.ingest(packet(0,"101"),request,user);}));}
            gate.countDown();var ids=new HashSet<Object>();for(var future:futures)ids.add(future.get(20,java.util.concurrent.TimeUnit.SECONDS).get("observationId"));
            assertThat(ids).hasSize(1);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM meter_observation WHERE meter_id=?",Long.class,meter)).isEqualTo(1);
        }finally{pool.shutdownNow();}
    }
    @Test void diagnosticReadingsDoNotAdvanceFinancialCursorAndFormalReadingBillsEntireDelta(){
        observations.ingest(packet(0,"101"),key+"1",user);
        observations.ingest(packet(15,"102"),key+"2",user);
        assertThat(jdbc.queryForObject("SELECT current_reading FROM water_meter WHERE id=?",java.math.BigDecimal.class,meter)).isEqualByComparingTo("100");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bill WHERE user_id=?",Long.class,user)).isZero();
        collection.ingest(packet(30,"103"));
        assertThat(jdbc.queryForObject("SELECT usage_amount FROM bill WHERE user_id=?",java.math.BigDecimal.class,user)).isEqualByComparingTo("3");
    }
    @Test void tenDuplicatesCountAsOneObservationAndConflictingReplayCannotOverwriteIt(){
        for(int i=0;i<10;i++)observations.ingest(packet(0,"101"),key+i,user);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM meter_observation WHERE meter_id=?",Long.class,meter)).isEqualTo(1);
        assertThatThrownBy(()->observations.ingest(packet(0,"102"),key+"conflict",user)).hasMessageContaining("冲突");
        assertThat(jdbc.queryForObject("SELECT total FROM meter_observation WHERE meter_id=?",java.math.BigDecimal.class,meter)).isEqualByComparingTo("101");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM observation_attempt WHERE meter_id=? AND validation_code='rejected'",Long.class,meter)).isEqualTo(1);
    }
    @Test void requestIdIsBoundToBodyAndOwner(){
        var result=observations.ingest(packet(0,"101"),key,user);
        assertThat(observations.ingest(packet(0,"101"),key,user).get("observationId")).isEqualTo(result.get("observationId"));
        assertThatThrownBy(()->observations.ingest(packet(15,"102"),key,user)).hasMessageContaining("请求号");
        assertThatThrownBy(()->observations.ingest(packet(0,"101"),key,user+1)).hasMessageContaining("请求号");
    }
    @Test void BackwardAndStaleReadingsDoNotMoveObservationCursor(){
        observations.ingest(packet(15,"102"),key+"1",user);
        assertThatThrownBy(()->observations.ingest(packet(30,"101"),key+"2",user)).hasMessageContaining("倒退");
        assertThatThrownBy(()->observations.ingest(packet(0,"103"),key+"3",user)).hasMessageContaining("乱序");
        assertThat(jdbc.queryForObject("SELECT last_valid_total FROM observation_cursor WHERE meter_id=?",java.math.BigDecimal.class,meter)).isEqualByComparingTo("102");
    }
}
