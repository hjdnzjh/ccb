package com.water.ai.meter.diagnosis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.water.ai.meter.controller.WorkOrderController;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="BILLING_MYSQL_TESTS",matches="true")
@SpringBootTest(properties={"spring.datasource.url=jdbc:mysql://localhost:3308/water_meter_billing_test?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true","spring.datasource.username=root","spring.datasource.password=123456","spring.main.web-application-type=none","spring.cloud.nacos.discovery.enabled=false","automation.scheduling.enabled=false","diagnosis.scheduling.enabled=false","logging.level.root=WARN","logging.level.com.water.ai.meter=WARN","logging.file.name=../logs/innovation-tests.log","mybatis-plus.configuration.log-impl=org.apache.ibatis.logging.nologging.NoLoggingImpl"})
class VerificationMySqlIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired PlatformTransactionManager transactions;
    @Autowired WorkOrderController orders;
    WorkOrderVerificationService service;
    MutableClock clock;
    long user, meter, anomaly, order, caseId;
    String key;
    final LocalDateTime start = LocalDateTime.of(2026,9,1,8,0);

    @BeforeEach void setup() {
        assertThat(jdbc.queryForObject("SELECT DATABASE()",String.class)).isEqualTo("water_meter_billing_test");
        clock = new MutableClock(start);
        service = new WorkOrderVerificationService(jdbc,json,clock,transactions);
        key="VERIFY_"+UUID.randomUUID().toString().replace("-","");
        jdbc.update("INSERT INTO sys_user(username,password) VALUES(?,'test')",key);
        user=jdbc.queryForObject("SELECT id FROM sys_user WHERE username=?",Long.class,key);
        jdbc.update("INSERT INTO water_meter(meter_no,user_id,status,current_reading) VALUES(?,?,0,100)",key,user);
        meter=jdbc.queryForObject("SELECT id FROM water_meter WHERE meter_no=?",Long.class,key);
        jdbc.update("INSERT INTO anomaly_record(anomaly_no,meter_id,user_id,anomaly_type,status,handle_result) VALUES(?,?,?,'high_flow',2,'original conclusion')",key,meter,user);
        anomaly=jdbc.queryForObject("SELECT id FROM anomaly_record WHERE anomaly_no=?",Long.class,key);
        jdbc.update("INSERT INTO work_order(order_no,meter_id,user_id,anomaly_id,status,result,complete_time) VALUES(?,?,?,?,3,'original conclusion',?)",key,meter,user,anomaly,start);
        order=jdbc.queryForObject("SELECT id FROM work_order WHERE order_no=?",Long.class,key);
        jdbc.update("UPDATE anomaly_record SET work_order_id=? WHERE id=?",order,anomaly);
        jdbc.update("INSERT INTO diagnosis_case(meter_id,user_id,family,state,severity,summary,mode,policy_version,anomaly_id,work_order_id,opened_at,last_evidence_at) VALUES(?,?,'high_flow','manual_review','high','test','assisted',1,?,?,?,?)",meter,user,anomaly,order,start.minusHours(1),start.minusHours(1));
        caseId=jdbc.queryForObject("SELECT id FROM diagnosis_case WHERE work_order_id=?",Long.class,order);
    }

    @AfterEach void cleanup() {
        jdbc.update("DELETE FROM diagnosis_review WHERE case_id=?",caseId);
        jdbc.update("DELETE FROM work_order_verification WHERE case_id=?",caseId);
        jdbc.update("DELETE FROM diagnosis_evidence WHERE case_id=?",caseId);
        jdbc.update("DELETE FROM diagnosis_case_guard WHERE meter_id=?",meter);
        jdbc.update("DELETE FROM diagnosis_case WHERE meter_id=?",meter);
        jdbc.update("DELETE FROM meter_observation WHERE meter_id=?",meter);
        jdbc.update("DELETE FROM work_order WHERE meter_id=?",meter);
        jdbc.update("DELETE FROM anomaly_record WHERE meter_id=?",meter);
        jdbc.update("DELETE FROM water_meter WHERE id=?",meter);
        jdbc.update("DELETE FROM sys_user WHERE id=?",user);
    }

    @Test void startIsIdempotentAndNormalDayRecoversWithoutRewritingHistory() {
        service.startForOrder(order); service.startForOrder(order);
        samples(96,"OPEN","0",0.1);
        clock.at(start.plusHours(24)); service.tick();
        assertThat(service.list(order)).hasSize(1);
        assertThat(state()).isEqualTo("recovered");
        assertThat(jdbc.queryForObject("SELECT result FROM work_order WHERE id=?",String.class,order)).isEqualTo("original conclusion");
        assertThat(jdbc.queryForObject("SELECT status FROM anomaly_record WHERE id=?",Integer.class,anomaly)).isEqualTo(2);
    }

    @Test void sparseDataWaitsThenBecomesInconclusive() {
        service.startForOrder(order); sample(15,"OPEN","0",0.1);
        clock.at(start.plusHours(24)); service.tick(); assertThat(state()).isEqualTo("observing");
        clock.at(start.plusHours(72)); service.tick(); assertThat(state()).isEqualTo("inconclusive");
    }

    @Test void closedValveNeverCountsAsRecovery() {
        service.startForOrder(order); samples(96,"CLOSED","0",0);
        clock.at(start.plusHours(24)); service.tick(); assertThat(state()).isEqualTo("observing");
        clock.at(start.plusHours(72)); service.tick(); assertThat(state()).isEqualTo("inconclusive");
    }

    @Test void disabledOrReplacedMeterCannotRecover() {
        service.startForOrder(order); samples(96,"OPEN","0",0.1);
        jdbc.update("UPDATE water_meter SET status=3 WHERE id=?",meter);
        clock.at(start.plusHours(24)); service.tick(); assertThat(state()).isEqualTo("observing");
    }

    @Test void hardAlarmIsPersistentImmediately() {
        service.startForOrder(order); sample(15,"OPEN","E01",0.1);
        clock.at(start.plusMinutes(15)); service.tick(); assertThat(state()).isEqualTo("persistent");
    }

    @Test void threeDistinctAbnormalWindowsArePersistentButBurstDuplicatesAreNot() {
        service.startForOrder(order);
        sample(1,"OPEN","0",11); sample(2,"OPEN","0",11); sample(3,"OPEN","0",11);
        clock.at(start.plusMinutes(3)); service.tick(); assertThat(state()).isEqualTo("observing");
        sample(16,"OPEN","0",11); sample(31,"OPEN","0",11);
        clock.at(start.plusMinutes(31)); service.tick(); assertThat(state()).isEqualTo("persistent");
    }

    @Test void coverageDoesNotHideLongGap() {
        service.startForOrder(order);
        for(int i=1;i<=96;i++) if(i<20 || i>23) sample(i*15,"OPEN","0",0.1);
        clock.at(start.plusHours(24)); service.tick(); assertThat(state()).isEqualTo("observing");
    }

    @Test void followupIsPersistentlyIdempotentAndKeepsOriginalRecords() {
        service.startForOrder(order); sample(15,"OPEN","E01",0.1);
        clock.at(start.plusMinutes(15)); service.tick();
        long next=service.followup(order,"现场复查",key+"-review",user);
        assertThat(service.followup(order,"现场复查",key+"-review",user)).isEqualTo(next);
        assertThat(service.followup(order,"再次点击",key+"-review2",user)).isEqualTo(next);
        assertThat(next).isNotEqualTo(order);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM work_order WHERE meter_id=?",Long.class,meter)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT anomaly_id FROM work_order WHERE id=?",Long.class,next)).isNotEqualTo(anomaly);
        assertThat(jdbc.queryForObject("SELECT handle_result FROM anomaly_record WHERE id=?",String.class,anomaly)).isEqualTo("original conclusion");
        assertThat(jdbc.queryForObject("SELECT reviewer_id FROM diagnosis_review WHERE request_key=?",Long.class,key+"-review")).isEqualTo(user);
    }

    @Test void completedOrderWithoutDiagnosisDoesNotStartVerification() {
        jdbc.update("DELETE FROM diagnosis_case WHERE id=?",caseId);
        service.startForOrder(order); assertThat(service.list(order)).isEmpty();
    }

    @Test void completingLinkedOrderStartsVerificationInSameTransaction() {
        jdbc.update("UPDATE work_order SET status=2 WHERE id=?",order);
        orders.complete(order,Map.of("result","维修完成"));
        orders.complete(order,Map.of("result","重复完成"));
        assertThat(service.list(order)).hasSize(1);
    }

    String state(){return (String)service.list(order).get(0).get("state");}
    @Test void restartAfterDeadlineCannotUseLaterDayToClaimRecovery(){service.startForOrder(order);for(int i=289;i<=384;i++)sample(i*15,"OPEN","0",.01);clock.at(start.plusHours(96));service.tick();assertThat(state()).isEqualTo("inconclusive");}
    @Test void newerNormalSampleCannotHideAlarmInSameSlot(){service.startForOrder(order);sample(1,"OPEN","E01",.01);sample(2,"OPEN","0",.01);samples(96,"OPEN","0",.01);clock.at(start.plusHours(24));service.tick();assertThat(state()).isEqualTo("persistent");}
    @Test void sameSlotReopenCannotProveSupplyWasComparable(){service.startForOrder(order);sample(1,"CLOSED","0",0);sample(2,"OPEN","0",.01);samples(96,"OPEN","0",.01);clock.at(start.plusHours(24));service.tick();assertThat(state()).isEqualTo("observing");}
    @Test void isolatedMissingGridsDoNotCreateValidBridgingIntervals(){usageProfile(1,1);sample(0,"OPEN","0",.01);for(int i=1;i<=96;i++)if(i>80||i%10!=0)sample(i*15,"OPEN","0",.01);clock.at(start.plusHours(24));service.tick();assertThat(state()).isEqualTo("observing");assertThat(((Number)service.list(order).get(0).get("coverage")).doubleValue()).isLessThan(.9);}
    void usageProfile(double dayUpper,double nightUpper){
        jdbc.update("UPDATE diagnosis_case SET family='usage' WHERE id=?",caseId);service.startForOrder(order);
        Map<String,Object> groups=new HashMap<>();for(int weekend=0;weekend<2;weekend++)for(int slot=0;slot<6;slot++)groups.put(weekend+":"+slot,Map.of("ready",true,"upper",slot<2?nightUpper:dayUpper));
        jdbc.update("UPDATE work_order_verification SET baseline_snapshot=? WHERE work_order_id=?",DiagnosisSupport.json(Map.of("upper",nightUpper,"groups",groups)),order);
    }
    @Test void lowInstantFlowCannotHidePersistentCumulativeUsage(){usageProfile(.1,.1);samples(96,"OPEN","0",.01);clock.at(start.plusHours(24));service.tick();assertThat(state()).isEqualTo("persistent");}
    @Test void normalDaytimePeaksUseTheirOwnFrozenGroup(){usageProfile(1,.1);double total=100;sample(0,"OPEN","0",.01);for(int i=1;i<=96;i++){int hour=start.plusMinutes(i*15).getHour();double rate=hour<8?.04:.8;total+=rate/4;sample(i*15,"OPEN","0",rate);jdbc.update("UPDATE meter_observation SET total=? WHERE meter_id=? AND reported_at=?",total,meter,start.plusMinutes(i*15));}clock.at(start.plusHours(24));service.tick();assertThat(state()).isEqualTo("recovered");}
    @Test void nightAnomalyIsNotHiddenByDaytimeUpper(){usageProfile(1,.1);double total=100;sample(0,"OPEN","0",.01);for(int i=1;i<=96;i++){int hour=start.plusMinutes(i*15).getHour();double rate=hour<8?.3:.8;total+=rate/4;sample(i*15,"OPEN","0",.01);jdbc.update("UPDATE meter_observation SET total=? WHERE meter_id=? AND reported_at=?",total,meter,start.plusMinutes(i*15));}clock.at(start.plusHours(24));service.tick();assertThat(state()).isEqualTo("persistent");}
    @Test void legacySingleGroupSnapshotCannotProveUsageRecovery(){jdbc.update("UPDATE diagnosis_case SET family='usage' WHERE id=?",caseId);service.startForOrder(order);jdbc.update("UPDATE work_order_verification SET baseline_snapshot='{}' WHERE work_order_id=?",order);samples(96,"OPEN","0",.01);clock.at(start.plusHours(24));service.tick();assertThat(state()).isEqualTo("observing");clock.at(start.plusHours(72));service.tick();assertThat(state()).isEqualTo("inconclusive");}
    void samples(int n,String valve,String alarm,double flow){for(int i=1;i<=n;i++)sample(i*15,valve,alarm,flow);}
    void sample(int minutes,String valve,String alarm,double flow) {
        jdbc.update("INSERT INTO meter_observation(meter_id,source,reported_at,received_at,flow,total,temperature,valve,alarm,packet_hash,packet,quality_status) VALUES(?,'simulation',?,?,?, ?,25,?,?,?,'test','valid')",meter,start.plusMinutes(minutes),start.plusMinutes(minutes),flow,100+minutes/100.0,valve,alarm,UUID.randomUUID().toString().replace("-",""));
    }
    static class MutableClock extends Clock {
        Instant instant;
        MutableClock(LocalDateTime time){at(time);}
        void at(LocalDateTime time){instant=time.atZone(getZone()).toInstant();}
        public ZoneId getZone(){return ZoneId.of("Asia/Shanghai");}
        public Clock withZone(ZoneId zone){return Clock.fixed(instant,zone);}
        public Instant instant(){return instant;}
    }
}
