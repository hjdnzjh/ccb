package com.water.ai.meter.diagnosis;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="BILLING_MYSQL_TESTS",matches="true")
@SpringBootTest(properties={"spring.datasource.url=jdbc:mysql://localhost:3308/water_meter_billing_test?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true","spring.datasource.username=root","spring.datasource.password=123456","spring.main.web-application-type=none","spring.cloud.nacos.discovery.enabled=false","automation.scheduling.enabled=false","diagnosis.scheduling.enabled=false","logging.level.root=WARN","logging.level.com.water.ai.meter=WARN","logging.file.name=../logs/innovation-tests.log","mybatis-plus.configuration.log-impl=org.apache.ibatis.logging.nologging.NoLoggingImpl"})
class DiagnosisPerformanceMySqlIntegrationTest {
 @Autowired JdbcTemplate jdbc;@Autowired DiagnosisService service;
 @Test void thousandMeterImportAndTenConcurrentListReaders() throws Exception {
  assertThat(jdbc.queryForObject("SELECT DATABASE()",String.class)).isEqualTo("water_meter_billing_test");String key="PERF_"+UUID.randomUUID().toString().replace("-","");
  jdbc.update("INSERT INTO sys_user(username,password) VALUES(?,'test')",key);long user=jdbc.queryForObject("SELECT id FROM sys_user WHERE username=?",Long.class,key);
  String digits="(SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9)";
  var pool=Executors.newFixedThreadPool(10);
  try{
   long begin=System.nanoTime();
   jdbc.update("INSERT INTO water_meter(meter_no,user_id,current_reading) SELECT CONCAT(?,'-',a.n*100+b.n*10+c.n),?,100 FROM "+digits+" a CROSS JOIN "+digits+" b CROSS JOIN "+digits+" c",key,user);
   jdbc.update("INSERT INTO meter_observation(meter_id,source,reported_at,flow,total,temperature,valve,alarm,packet_hash,packet) SELECT m.id,'simulated',DATE_ADD('2026-01-01',INTERVAL (a.n*10+b.n)*15 MINUTE),0.02,100+(a.n*10+b.n)*0.005,25,'OPEN','0',SHA2(CONCAT(m.id,':',a.n,':',b.n),256),'isolated-performance-fixture' FROM water_meter m CROSS JOIN "+digits+" a CROSS JOIN "+digits+" b WHERE m.user_id=? AND a.n*10+b.n<96",user);
   jdbc.update("INSERT INTO diagnosis_case(meter_id,user_id,family,state,severity,summary,mode,policy_version,opened_at,last_evidence_at) SELECT id,user_id,'usage','candidate','medium','性能验收模拟事件','shadow',1,NOW(),NOW() FROM water_meter WHERE user_id=?",user);
   double imported=(System.nanoTime()-begin)/1e9;assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM meter_observation o JOIN water_meter m ON m.id=o.meter_id WHERE m.user_id=?",Long.class,user)).isEqualTo(96000);
   for(int i=0;i<5;i++)service.cases(1,20,key,null,null);
   var times=Collections.synchronizedList(new ArrayList<Double>());var gate=new CountDownLatch(1);var futures=new ArrayList<Future<?>>();begin=System.nanoTime();
   for(int n=0;n<10;n++)futures.add(pool.submit(()->{gate.await();for(int i=0;i<20;i++){long started=System.nanoTime();var result=service.cases(1,20,key,null,null);assertThat(result.get("total")).isEqualTo(1000L);times.add((System.nanoTime()-started)/1e6);}return true;}));
   gate.countDown();for(var future:futures)future.get(60,TimeUnit.SECONDS);double duration=(System.nanoTime()-begin)/1e9;times.sort(Double::compareTo);double p95=times.get((int)Math.ceil(times.size()*.95)-1);
   var result=new LinkedHashMap<String,Object>();result.put("meters",1000);result.put("observations",96000);result.put("samplingMinutes",15);result.put("importSeconds",imported);result.put("concurrentReaders",10);result.put("requests",times.size());result.put("listDurationSeconds",duration);result.put("listP95Millis",p95);result.put("targetMet",p95<1000);result.put("measurement","JdbcTemplate import and service-layer event list; excludes HTTP, browser, inference and real devices");
   Path output=Path.of("../logs/innovation/performance-java.json");Files.createDirectories(output.getParent());Files.writeString(output,DiagnosisSupport.json(result));
  }finally{pool.shutdownNow();jdbc.update("DELETE o FROM meter_observation o JOIN water_meter m ON m.id=o.meter_id WHERE m.user_id=?",user);jdbc.update("DELETE FROM diagnosis_case WHERE user_id=?",user);jdbc.update("DELETE FROM water_meter WHERE user_id=?",user);jdbc.update("DELETE FROM sys_user WHERE id=?",user);}
 }
}
