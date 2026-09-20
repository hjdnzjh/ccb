package com.water.ai.meter.reporting;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="BILLING_MYSQL_TESTS",matches="true")
@SpringBootTest(properties={
        "spring.datasource.url=jdbc:mysql://localhost:3308/water_meter_billing_test?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true",
        "spring.datasource.username=root","spring.datasource.password=123456","spring.main.web-application-type=none",
        "spring.cloud.nacos.discovery.enabled=false","automation.scheduling.enabled=false",
        "management.health.elasticsearch.enabled=false","logging.file.name=../logs/reporting-tests.log","logging.level.root=WARN",
        "mybatis-plus.configuration.log-impl=org.apache.ibatis.logging.nologging.NoLoggingImpl"
})
class ReportingMySqlIntegrationTest {
    @Autowired ReportService service;
    @Autowired JdbcTemplate jdbc;
    long user,meter,deletedMeter,laterMeter,bill;
    String marker;
    List<String> snapshots=new ArrayList<>();
    @BeforeEach void fixture() {
        assertThat(jdbc.queryForObject("SELECT DATABASE()",String.class)).isEqualTo("water_meter_billing_test");
        marker="REPORT_IT_"+UUID.randomUUID().toString().replace("-","");
        jdbc.update("INSERT INTO sys_user(username,password,deleted) VALUES(?,'test',0)",marker);
        user=jdbc.queryForObject("SELECT id FROM sys_user WHERE username=?",Long.class,marker);
        meter=meter("a","1901-01-01 00:00:00",0);
        deletedMeter=meter("b","1901-01-01 00:00:00",1);
        laterMeter=meter("c","1901-03-01 12:00:00",0);
        reading("1901-02-28 00:00:00",10,1,0);
        reading("1901-03-01 23:59:59",7,1,0);
        reading("1901-03-02 00:00:00",90,1,0);
        reading("1901-02-28 15:00:00",100,0,0);
        reading("1901-02-28 15:00:00",100,1,1);
        jdbc.update("INSERT INTO bill(bill_no,user_id,bill_period,total_amount,paid_amount) VALUES(?,?,'1901-02',100,100)",marker,user);
        bill=jdbc.queryForObject("SELECT id FROM bill WHERE bill_no=?",Long.class,marker);
        payment("1","1901-02-28 00:00:00","3.25"); payment("2","1901-03-01 23:59:59","4.50"); payment("3","1901-03-02 00:00:00","90.00");
        anomaly("a",meter,"meter_fault","1901-02-28 00:00:00");
        anomaly("b",meter,"meter_fault","1901-02-28 23:59:59");
        anomaly("c",meter,"collection_failure","1901-03-01 12:00:00");
        anomaly("d",deletedMeter,"valve_fault","1901-03-01 23:59:59");
        anomaly("e",laterMeter,"high_flow","1901-03-01 23:59:59");
        anomaly("f",laterMeter,"meter_fault","1901-03-02 00:00:00");
    }
    long meter(String suffix,String created,int deleted) {
        jdbc.update("INSERT INTO water_meter(meter_no,user_id,create_time,deleted) VALUES(?,?,?,?)",marker+suffix,user,created,deleted);
        return jdbc.queryForObject("SELECT id FROM water_meter WHERE meter_no=?",Long.class,marker+suffix);
    }
    void reading(String time,int usage,int status,int deleted) { jdbc.update("INSERT INTO meter_reading(meter_id,user_id,reading_value,usage_amount,status,reading_time,deleted) VALUES(?,?,100,?,?,?,?)",meter,user,usage,status,time,deleted); }
    void payment(String suffix,String time,String amount) { jdbc.update("INSERT INTO bill_payment(bill_id,amount,pay_method,trade_no,paid_time) VALUES(?,?,'cash',?,?)",bill,amount,marker+suffix,time); }
    void anomaly(String suffix,long id,String type,String time) { jdbc.update("INSERT INTO anomaly_record(anomaly_no,meter_id,user_id,anomaly_type,detected_time,status,deleted) VALUES(?,?,?,?,?,2,0)",marker+suffix,id,user,type,time); }
    ReportSnapshot generate(String start,String end,ReportRange.Granularity grain) {
        var report=service.generate(new ReportRange(LocalDate.parse(start),LocalDate.parse(end),grain)); snapshots.add(report.id()); return report;
    }
    @AfterEach void cleanup() {
        for(String id:snapshots) jdbc.update("DELETE FROM report_snapshot WHERE id=?",id);
        jdbc.update("DELETE FROM anomaly_record WHERE user_id=?",user);
        jdbc.update("DELETE FROM bill_payment WHERE bill_id=?",bill);
        jdbc.update("DELETE FROM bill WHERE user_id=?",user);
        jdbc.update("DELETE FROM meter_reading WHERE user_id=?",user);
        jdbc.update("DELETE FROM water_meter WHERE user_id=?",user);
        jdbc.update("DELETE FROM sys_user WHERE id=?",user);
    }
    @Test void inclusiveDateBoundariesUseApprovedReadingsActualReceiptsAndDeduplicatedFaults() {
        var report=generate("1901-02-28","1901-03-01",ReportRange.Granularity.DAY);
        assertThat(report.totals().waterUsage()).isEqualByComparingTo("17");
        assertThat(report.totals().revenue()).isEqualByComparingTo("7.75");
        assertThat(report.totals().readingCount()).isEqualTo(2);
        assertThat(report.totals().paymentCount()).isEqualTo(2);
        assertThat(report.totals().faultMeters()).isEqualTo(2);
        assertThat(report.totals().meterBase()).isEqualTo(3);
        assertThat(report.totals().faultRate()).isEqualByComparingTo("66.67");
        assertThat(report.rows().get(0).metrics().faultMeters()).isEqualTo(1);
        assertThat(report.rows().get(0).metrics().meterBase()).isEqualTo(2);
        assertThat(report.rows().get(1).metrics().faultMeters()).isEqualTo(2);
        jdbc.update("UPDATE meter_reading SET usage_amount=999 WHERE meter_id=?",meter);
        assertThat(service.get(report.id())).isEqualTo(report);
    }
    @Test void everyGranularityKeepsSameRangeTotalsAndEmptyRangesAreExplicit() {
        for(var grain:ReportRange.Granularity.values()) {
            var report=generate("1901-02-28","1901-03-01",grain);
            assertThat(report.totals().waterUsage()).isEqualByComparingTo("17");
            assertThat(report.totals().revenue()).isEqualByComparingTo("7.75");
            assertThat(report.totals().faultMeters()).isEqualTo(2);
        }
        var empty=generate("1900-01-01","1900-01-02",ReportRange.Granularity.DAY);
        assertThat(empty.totals().waterUsage()).isZero();
        assertThat(empty.totals().revenue()).isZero();
        assertThat(empty.totals().meterBase()).isZero();
        assertThat(empty.totals().faultRate()).isNull();
        assertThat(empty.rows()).hasSize(2);
    }
}
