package com.water.ai.meter.service.impl;

import com.water.ai.meter.mapper.OpsMapper;
import com.water.ai.meter.service.BillService;
import com.water.ai.meter.operations.MeterAssetService;
import com.water.ai.meter.operations.AnomalyOperations;
import com.water.ai.meter.automation.AutonomousCoverageService;
import com.water.ai.meter.controller.WorkOrderController;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="BILLING_MYSQL_TESTS",matches="true")
@SpringBootTest(properties={"spring.datasource.url=jdbc:mysql://localhost:3308/water_meter_billing_test?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true", "spring.datasource.username=root","spring.datasource.password=123456","spring.main.web-application-type=none","spring.cloud.nacos.discovery.enabled=false","automation.scheduling.enabled=false","logging.level.root=WARN","logging.file.name=../logs/competition-tests.log","mybatis-plus.configuration.log-impl=org.apache.ibatis.logging.nologging.NoLoggingImpl"})
@Transactional
class CompetitionGapMySqlIntegrationTest {
 @Autowired JdbcTemplate jdbc;
 @Autowired OpsMapper ops;
 @Autowired BillService bills;
 @Autowired MeterAssetService assets;
 @Autowired AnomalyOperations anomalies;
 @Autowired AutonomousCoverageService coverage;
 @Autowired com.water.ai.meter.automation.CollectionService collection;
 @Autowired WorkOrderController orders;
 @Autowired org.mybatis.spring.SqlSessionTemplate sqlSession;
 String key;long user,area,meter;
 @BeforeEach void setup(){
  assertThat(jdbc.queryForObject("SELECT DATABASE()",String.class)).isEqualTo("water_meter_billing_test");
  key="GAP_"+UUID.randomUUID().toString().replace("-","");
  jdbc.update("INSERT INTO sys_user(username,password) VALUES(?,'test')",key);user=jdbc.queryForObject("SELECT id FROM sys_user WHERE username=?",Long.class,key);
  jdbc.update("INSERT INTO area(area_code,area_name,level) VALUES(?,'验收区域',3)",key);area=jdbc.queryForObject("SELECT id FROM area WHERE area_code=?",Long.class,key);
  jdbc.update("INSERT INTO water_meter(meter_no,user_id,area_id) VALUES(?,?,?)",key,user,area);meter=jdbc.queryForObject("SELECT id FROM water_meter WHERE meter_no=?",Long.class,key);
 }
 @Test void zoneUsageIsApprovedAndNotMultipliedByAnomalies(){
  jdbc.update("INSERT INTO meter_reading(meter_id,user_id,reading_value,usage_amount,status,reading_time) VALUES(?,?,100,7,1,NOW()),(?,?,200,90,0,NOW())",meter,user,meter,user);
  for(int i=0;i<2;i++)jdbc.update("INSERT INTO anomaly_record(anomaly_no,meter_id,anomaly_type,status) VALUES(?,?,'meter_fault',0)",key+i,meter);
  var row=ops.zoneOverview().stream().filter(r->((Number)r.get("id")).longValue()==area).findFirst().orElseThrow();
  assertThat(new BigDecimal(row.get("today_usage").toString())).isEqualByComparingTo("7");
  assertThat(((Number)row.get("anomaly_count")).intValue()).isEqualTo(2);
 }
 @Test void revenueHonorsEndDateAndUsesDatedReceipts(){
  jdbc.update("INSERT INTO bill(bill_no,user_id,bill_period,total_amount,paid_amount,create_time) VALUES(?,?,'1903-01',100,90,'1903-01-15'),(?,?,'1903-02',200,200,'1903-02-01')",key,user,key+"B",user);
  long bill=jdbc.queryForObject("SELECT id FROM bill WHERE bill_no=?",Long.class,key);
  jdbc.update("INSERT INTO bill_payment(bill_id,amount,pay_method,trade_no,paid_time) VALUES(?,1.25,'cash',?,'1903-01-31 23:59:59'),(?,2.75,'cash',?,'1903-02-01 00:00:00')",bill,key,bill,key+"B");
  var result=bills.getRevenueStatistics("1903-01-01","1903-01-31");
  assertThat(new BigDecimal(result.get("totalAmount").toString())).isEqualByComparingTo("100");
  assertThat(new BigDecimal(result.get("paidAmount").toString())).isEqualByComparingTo("1.25");
  assertThatIllegalArgumentException().isThrownBy(()->bills.getRevenueStatistics("1903-02-01","1903-01-01"));
 }
 @Test void assetsPersistWithoutOverwritingReadingAndProtectHistoricalIdentity(){
  long created=assets.save(null,new MeterAssetService.Input(key+"N","digital","NB-IoT",user,area,"测试地址","测试厂商",0));
  assertThat(jdbc.queryForObject("SELECT current_reading FROM water_meter WHERE id=?",BigDecimal.class,created)).isEqualByComparingTo("0");
  jdbc.update("UPDATE water_meter SET current_reading=123.45 WHERE id=?",created);
  assets.save(created,new MeterAssetService.Input(key+"N","digital","4G",user,area,"新地址","厂商",1));
  assertThat(jdbc.queryForObject("SELECT current_reading FROM water_meter WHERE id=?",BigDecimal.class,created)).isEqualByComparingTo("123.45");
  jdbc.update("INSERT INTO meter_reading(meter_id,user_id,reading_value,usage_amount,status,reading_time) VALUES(?,?,123.45,1,1,NOW())",created,user);
  assertThatIllegalArgumentException().isThrownBy(()->assets.save(created,new MeterAssetService.Input(key+"X","digital","4G",user,area,"地址","厂商",1)));
  assertThatIllegalArgumentException().isThrownBy(()->assets.save(null,new MeterAssetService.Input(key+"Z","digital","4G",-1L,area,"地址","厂商",0)));
 }
 long alarm(){
  jdbc.update("INSERT INTO anomaly_record(anomaly_no,meter_id,user_id,anomaly_type,severity,status) VALUES(?,?,?,'meter_fault','critical',0)",key,meter,user);
  return jdbc.queryForObject("SELECT id FROM anomaly_record WHERE anomaly_no=?",Long.class,key);
 }
 @Test void anomalyOrderIsIdempotentAndCompletionClosesAnomaly(){
  long alarm=alarm(),order=anomalies.workOrder(alarm);
  assertThat(anomalies.workOrder(alarm)).isEqualTo(order);
  assertThatIllegalArgumentException().isThrownBy(()->anomalies.resolve(alarm,2,"已核查","tester"));
  assertThat(orders.complete(order,Map.of("result","修复故障")).isSuccess()).isFalse();
  assertThat(orders.accept(order).isSuccess()).isTrue();
  assertThat(orders.complete(order,Map.of("result"," ")).isSuccess()).isFalse();
  assertThat(orders.complete(order,Map.of("result","修复故障")).isSuccess()).isTrue();
  assertThat(((Number)anomalies.detail(alarm).get("status")).intValue()).isEqualTo(2);
  assertThat(anomalies.detail(alarm).get("handle_result")).isEqualTo("修复故障");
  assertThat(orders.complete(order,Map.of("result","覆盖结论")).isSuccess()).isFalse();
 }
 @Test void cancelledOrderReopensAnomalyAndAllowsNewOrder(){
  long alarm=alarm(),order=anomalies.workOrder(alarm);
  assertThat(orders.close(order,Map.of("remark","需要重新分派")).isSuccess()).isTrue();
  assertThat(((Number)anomalies.detail(alarm).get("status")).intValue()).isZero();
  assertThat(anomalies.detail(alarm).get("work_order_id")).isNull();
  assertThat(orders.close(order,Map.of("remark","重复关闭")).isSuccess()).isFalse();
  assertThat(anomalies.workOrder(alarm)).isNotEqualTo(order);
 }
 @Test void coverageEnrollsNewMetersOnceAndStopsIneligibleMeters(){
  coverage.save(new AutonomousCoverageService.Input(true,1440,new BigDecimal("1.00")));
  long plan=jdbc.queryForObject("SELECT plan_id FROM automation_coverage_meter WHERE meter_id=?",Long.class,meter);
  assertThatIllegalArgumentException().isThrownBy(()->collection.enabled(plan,false)).withMessageContaining("覆盖策略");
  coverage.reconcile();
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM automation_coverage_meter WHERE meter_id=?",Integer.class,meter)).isEqualTo(1);
  long added=assets.save(null,new MeterAssetService.Input(key+"N","digital","NB-IoT",user,area,"地址","厂商",0));
  coverage.reconcile();
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM automation_coverage_meter WHERE meter_id=?",Integer.class,added)).isEqualTo(1);
  jdbc.update("UPDATE water_meter SET status=2 WHERE id=?",meter);coverage.reconcile();
  assertThat(jdbc.queryForObject("SELECT enabled FROM automation_plan WHERE id=?",Integer.class,plan)).isZero();
  coverage.save(new AutonomousCoverageService.Input(false,1440,new BigDecimal("1.00")));
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM automation_plan p JOIN automation_coverage_meter c ON c.plan_id=p.id WHERE p.enabled=1",Integer.class)).isZero();
 }
 @Test void coverageDefersToEnabledManualPlans(){
  jdbc.update("INSERT INTO automation_plan(name,interval_minutes,enabled,next_run_at) VALUES(?,60,1,NOW())",key);
  long plan=jdbc.queryForObject("SELECT id FROM automation_plan WHERE name=?",Long.class,key);
  jdbc.update("INSERT INTO automation_plan_meter(plan_id,meter_id) VALUES(?,?)",plan,meter);
  coverage.save(new AutonomousCoverageService.Input(true,1440,new BigDecimal("1.00")));
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM automation_coverage_meter WHERE meter_id=?",Integer.class,meter)).isZero();
  jdbc.update("UPDATE automation_plan SET enabled=0 WHERE id=?",plan);coverage.reconcile();
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM automation_coverage_meter WHERE meter_id=?",Integer.class,meter)).isEqualTo(1);
 }
 @Test void automaticallyPlannedCollectionCreatesOneReadingAndBill(){
  coverage.save(new AutonomousCoverageService.Input(true,1440,new BigDecimal("1.00")));
  long plan=jdbc.queryForObject("SELECT plan_id FROM automation_coverage_meter WHERE meter_id=?",Long.class,meter);
  long run=collection.trigger(plan,key);
  long task=jdbc.queryForObject("SELECT id FROM automation_task WHERE run_id=?",Long.class,run);
  collection.processTask(task);
  collection.processTask(task);
  assertThat(jdbc.queryForObject("SELECT status FROM automation_task WHERE id=?",String.class,task)).isEqualTo("success");
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM meter_reading WHERE meter_id=?",Integer.class,meter)).isEqualTo(1);
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bill WHERE meter_id=?",Integer.class,meter)).isEqualTo(1);
 }
 @Test void currentOutstandingIncludesPartialPayments(){
  BigDecimal before=new BigDecimal(ops.unpaidSummary().get("unpaid_amount").toString());
  jdbc.update("INSERT INTO bill(bill_no,user_id,bill_period,total_amount,paid_amount,status) VALUES(?,?,'1903-01',100,40,3)",key,user);
  // Fixture writes use JDBC, which cannot invalidate MyBatis' session cache.
  sqlSession.clearCache();
  assertThat(new BigDecimal(ops.unpaidSummary().get("unpaid_amount").toString()).subtract(before)).isEqualByComparingTo("60");
 }
}
