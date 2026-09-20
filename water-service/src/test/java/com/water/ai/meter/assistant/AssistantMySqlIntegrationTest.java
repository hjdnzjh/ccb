package com.water.ai.meter.assistant;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="BILLING_MYSQL_TESTS",matches="true")
@SpringBootTest(properties={
 "spring.datasource.url=jdbc:mysql://localhost:3308/water_meter_billing_test?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true",
 "spring.datasource.username=root","spring.datasource.password=123456","spring.main.web-application-type=none",
 "spring.cloud.nacos.discovery.enabled=false","automation.scheduling.enabled=false","logging.level.root=WARN",
 "logging.file.name=../logs/assistant-tests.log","mybatis-plus.configuration.log-impl=org.apache.ibatis.logging.nologging.NoLoggingImpl"
})
@Transactional
class AssistantMySqlIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired AssistantRepository repository;
    String marker;long area,other,meter,user;
    @BeforeEach void setup() {
        assertThat(jdbc.queryForObject("SELECT DATABASE()",String.class)).isEqualTo("water_meter_billing_test");
        marker="QA_"+UUID.randomUUID().toString().replace("-","");
        jdbc.update("INSERT INTO sys_user(username,password) VALUES(?,'test')",marker);
        user=jdbc.queryForObject("SELECT id FROM sys_user WHERE username=?",Long.class,marker);
        jdbc.update("INSERT INTO area(area_code,area_name) VALUES(?,?)",marker,"测试区");
        area=jdbc.queryForObject("SELECT id FROM area WHERE area_code=?",Long.class,marker);
        jdbc.update("INSERT INTO area(area_code,area_name) VALUES(?,?)",marker+"B","其他区");
        other=jdbc.queryForObject("SELECT id FROM area WHERE area_code=?",Long.class,marker+"B");
        meter=addMeter("a",area,0,0);
    }
    long addMeter(String suffix,long region,int status,int deleted) {
        jdbc.update("INSERT INTO water_meter(meter_no,area_id,status,deleted) VALUES(?,?,?,?)",marker+suffix,region,status,deleted);
        return jdbc.queryForObject("SELECT id FROM water_meter WHERE meter_no=?",Long.class,marker+suffix);
    }
    void anomaly(long id,int status,int deleted,String suffix) {
        jdbc.update("INSERT INTO anomaly_record(anomaly_no,meter_id,anomaly_type,severity,status,deleted) VALUES(?,?,'night_usage','high',?,?)",marker+suffix,id,status,deleted);
    }
    @Test void filtersClosedDeletedStoppedAndOtherAreaWithoutMultiplyingCounts() {
        anomaly(meter,0,0,"1");anomaly(meter,1,0,"2");anomaly(meter,2,0,"3");anomaly(meter,0,1,"4");
        anomaly(addMeter("other",other,0,0),0,0,"5");
        anomaly(addMeter("stopped",area,2,0),0,0,"6");
        anomaly(addMeter("deleted",area,0,1),0,0,"7");
        var rows=repository.meters(List.of(area),null);
        assertThat(rows).hasSize(1);
        assertThat(((Number)rows.get(0).get("open_count")).intValue()).isEqualTo(2);
        assertThat(repository.leakEvidence(List.of(area),null)).hasSize(2);
        assertThat(repository.meters(List.of(other),marker+"a")).isEmpty();
    }
    @Test void receiptsUseActualRowsAndHalfOpenDatetimeBoundaries() {
        jdbc.update("INSERT INTO bill(bill_no,user_id,bill_period,total_amount,paid_amount,status) VALUES(?,?,'1902-02',100,99,3)",marker,user);
        long bill=jdbc.queryForObject("SELECT id FROM bill WHERE bill_no=?",Long.class,marker);
        for(String[] p:List.of(new String[]{"1","1902-02-01 00:00:00","3.25"},new String[]{"2","1902-02-10 11:59:59","4.50"},new String[]{"3","1902-02-10 12:00:00","9.00"}))
            jdbc.update("INSERT INTO bill_payment(bill_id,amount,pay_method,trade_no,paid_time) VALUES(?,?,'cash',?,?)",bill,p[2],marker+p[0],p[1]);
        var result=repository.receipts(LocalDateTime.of(1902,2,1,0,0),LocalDateTime.of(1902,2,10,12,0));
        assertThat((BigDecimal)result.get("amount")).isEqualByComparingTo("7.75");
        assertThat(((Number)result.get("count")).intValue()).isEqualTo(2);
    }
    @Test void unpaidIncludesPartialPaymentAndExcludesDeletedAndSettled() {
        var before=repository.unpaid(LocalDateTime.of(1902,2,10,12,0));
        jdbc.update("INSERT INTO bill(bill_no,user_id,bill_period,total_amount,paid_amount,status,deleted,due_date) VALUES(?,?,'1902-01',100,40,3,0,'1902-01-01'),(?,?,'1902-01',100,100,1,0,'1902-01-01'),(?,?,'1902-01',100,0,0,1,'1902-01-01')",marker+"1",user,marker+"2",user,marker+"3",user);
        var after=repository.unpaid(LocalDateTime.of(1902,2,10,12,0));
        assertThat(((BigDecimal)after.get("amount")).subtract((BigDecimal)before.get("amount"))).isEqualByComparingTo("60");
        assertThat(((Number)after.get("count")).longValue()-((Number)before.get("count")).longValue()).isEqualTo(1);
    }
}
