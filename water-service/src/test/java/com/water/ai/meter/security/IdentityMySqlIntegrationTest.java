package com.water.ai.meter.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named="BILLING_MYSQL_TESTS",matches="true")
@SpringBootTest(properties={
 "spring.datasource.url=jdbc:mysql://localhost:3308/water_meter_billing_test?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true",
 "spring.datasource.username=root","spring.datasource.password=123456","spring.cloud.nacos.discovery.enabled=false",
 "automation.scheduling.enabled=false","logging.file.name=../logs/identity-tests.log","logging.level.root=WARN","logging.level.com.water.ai.meter=WARN"})
@AutoConfigureMockMvc
class IdentityMySqlIntegrationTest {
 @Autowired MockMvc mvc;
 @Autowired JdbcTemplate jdbc;
 @Autowired ObjectMapper json;
 String marker,first,second,admin,token1,token2,adminToken;long user1,user2,adminId,billId;
 @BeforeEach void setup() throws Exception {
  assertThat(jdbc.queryForObject("SELECT DATABASE()",String.class)).isEqualTo("water_meter_billing_test");
  marker="AUTH_"+UUID.randomUUID().toString().replace("-","");first=marker+"a";second=marker+"b";admin=marker+"c";
  user1=user(first);user2=user(second);adminId=user(admin);
  jdbc.update("INSERT INTO auth_role(user_id,role) VALUES (?,'admin')",adminId);
  jdbc.update("INSERT INTO bill(bill_no,user_id,total_amount,water_fee,paid_amount,status,bill_period,due_date) VALUES (?,?,100,100,0,0,'2026-09',DATE_SUB(NOW(),INTERVAL 1 DAY))",marker,user1);
  billId=jdbc.queryForObject("SELECT id FROM bill WHERE bill_no=?",Long.class,marker);
  token1=login(first);token2=login(second);adminToken=login(admin);
 }
 long user(String name) {jdbc.update("INSERT INTO sys_user(username,password,user_type,status,deleted) VALUES (?,'Password123','residential',0,0)",name);return jdbc.queryForObject("SELECT id FROM sys_user WHERE username=?",Long.class,name);}
 String login(String name) throws Exception {
  String response=mvc.perform(post("/api/v1/auth/login").contentType("application/json").content(json.writeValueAsString(Map.of("username",name,"password","Password123"))))
   .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true)).andReturn().getResponse().getContentAsString();
  return json.readTree(response).path("data").path("token").asText();
 }
 @AfterEach void cleanup() {
  jdbc.update("DELETE FROM user_feedback WHERE user_id IN (?,?,?)",user1,user2,adminId);
  jdbc.update("DELETE FROM bill_payment WHERE bill_id=?",billId);jdbc.update("DELETE FROM bill WHERE id=?",billId);
  jdbc.update("DELETE FROM auth_session WHERE user_id IN (?,?,?)",user1,user2,adminId);
  jdbc.update("DELETE FROM auth_role WHERE user_id IN (?,?,?)",user1,user2,adminId);
  jdbc.update("DELETE FROM sys_user WHERE id IN (?,?,?)",user1,user2,adminId);
 }
 @Test void anonymousAndUserCannotReachAdministratorOrAgentEndpoints() throws Exception {
  for(String url:List.of("/api/v1/bill/list","/api/v1/me/overview","/agent-api/status")) mvc.perform(get(url)).andExpect(status().isUnauthorized());
  for(String url:List.of("/api/v1/bill/list","/api/v1/user/list","/api/v1/automation/plans","/api/v1/reports/1","/agent-api/status"))
   mvc.perform(get(url).header("Authorization","Bearer "+token1)).andExpect(status().isForbidden());
  mvc.perform(get("/api/v1/bill/list").header("Authorization","Bearer "+adminToken)).andExpect(status().isOk());
 }
 @Test void usersCanOnlyReadOrPayTheirOwnBills() throws Exception {
  mvc.perform(get("/api/v1/me/bills/"+billId).header("Authorization","Bearer "+token1)).andExpect(status().isOk()).andExpect(jsonPath("$.userId").value(user1));
  mvc.perform(get("/api/v1/me/overview").header("Authorization","Bearer "+token2)).andExpect(status().isOk()).andExpect(jsonPath("$.data.bills.length()").value(0));
  for(String suffix:List.of("","/payments")) mvc.perform(get("/api/v1/me/bills/"+billId+suffix).header("Authorization","Bearer "+token2)).andExpect(status().isNotFound());
  mvc.perform(post("/api/v1/me/bills/"+billId+"/pay").header("Authorization","Bearer "+token2).param("amount","40").param("payMethod","cash").param("tradeNo",marker)).andExpect(status().isNotFound());
  assertThat(jdbc.queryForObject("SELECT paid_amount FROM bill WHERE id=?",BigDecimal.class,billId)).isEqualByComparingTo("0");
  mvc.perform(post("/api/v1/me/bills/"+billId+"/pay").header("Authorization","Bearer "+token1).param("amount","40").param("payMethod","cash").param("tradeNo",marker)).andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bill_payment WHERE bill_id=?",Integer.class,billId)).isEqualTo(1);
 }
 @Test void logoutExpiryAndDisabledAccountsInvalidateSessions() throws Exception {
  mvc.perform(post("/api/v1/auth/logout").header("Authorization","Bearer "+token1)).andExpect(status().isOk());
  mvc.perform(get("/api/v1/auth/me").header("Authorization","Bearer "+token1)).andExpect(status().isUnauthorized());
  jdbc.update("UPDATE auth_session SET expires_at=DATE_SUB(NOW(),INTERVAL 1 SECOND) WHERE user_id=?",user2);
  mvc.perform(get("/api/v1/auth/me").header("Authorization","Bearer "+token2)).andExpect(status().isUnauthorized());
  jdbc.update("UPDATE sys_user SET status=1 WHERE id=?",adminId);
  mvc.perform(get("/api/v1/auth/me").header("Authorization","Bearer "+adminToken)).andExpect(status().isUnauthorized());
 }
 @Test void passwordUpgradeIsSaltedAndDatabaseContainsOnlyTokenDigest() throws Exception {
  assertThat(jdbc.queryForObject("SELECT password FROM sys_user WHERE id=?",String.class,user1)).startsWith("$2");
  assertThat(jdbc.queryForObject("SELECT token_hash FROM auth_session WHERE user_id=?",String.class,user1)).isEqualTo(SessionService.hash(token1)).isNotEqualTo(token1);
  mvc.perform(post("/api/v1/auth/login").contentType("application/json").content(json.writeValueAsString(Map.of("username",first,"password","admin123"))))
   .andExpect(status().isUnauthorized());
  mvc.perform(get("/api/v1/user/"+user1).header("Authorization","Bearer "+adminToken)).andExpect(status().isOk()).andExpect(jsonPath("$.data.password").doesNotExist());
 }
 @Test void feedbackIsPrivateAndAdministratorReplyIsVisibleToOwner() throws Exception {
  var result=mvc.perform(post("/api/v1/me/feedback").header("Authorization","Bearer "+token1).contentType("application/json").content("{\"subject\":\"水表故障\",\"content\":\"阀门关闭仍有流量\"}"))
   .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
  long feedbackId=json.readTree(result).path("data").path("id").asLong();
  mvc.perform(get("/api/v1/me/feedback").header("Authorization","Bearer "+token2)).andExpect(jsonPath("$.data.length()").value(0));
  mvc.perform(post("/api/v1/feedback/"+feedbackId+"/reply").header("Authorization","Bearer "+adminToken).contentType("application/json").content("{\"reply\":\"已安排检修\",\"status\":\"processing\"}"))
   .andExpect(status().isOk());
  mvc.perform(get("/api/v1/me/feedback").header("Authorization","Bearer "+token1)).andExpect(jsonPath("$.data[0].reply").value("已安排检修"));
 }
}
