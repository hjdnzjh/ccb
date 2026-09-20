package com.water.ai.meter.notification;

import com.water.ai.meter.security.SessionUser;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="BILLING_MYSQL_TESTS",matches="true")
@SpringBootTest(properties={"spring.datasource.url=jdbc:mysql://localhost:3308/water_meter_billing_test?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true","spring.datasource.username=root","spring.datasource.password=123456","spring.main.web-application-type=none","spring.cloud.nacos.discovery.enabled=false","automation.scheduling.enabled=false","logging.level.root=WARN","logging.level.com.water.ai.meter=WARN","logging.file.name=../logs/notification-tests.log","mybatis-plus.configuration.log-impl=org.apache.ibatis.logging.nologging.NoLoggingImpl"})
class NotificationMySqlIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired NotificationService service;
    long owner,other;String key;SessionUser user,admin,secondAdmin;
    @BeforeEach void setup(){
        assertThat(jdbc.queryForObject("SELECT DATABASE()",String.class)).isEqualTo("water_meter_billing_test");
        key="NOTICE_"+UUID.randomUUID().toString().replace("-","");
        jdbc.update("INSERT INTO sys_user(username,password) VALUES(?,'test'),(?,'test')",key,key+"B");
        owner=jdbc.queryForObject("SELECT id FROM sys_user WHERE username=?",Long.class,key);
        other=jdbc.queryForObject("SELECT id FROM sys_user WHERE username=?",Long.class,key+"B");
        user=new SessionUser(owner,key,key,"user");admin=new SessionUser(owner,key,key,"admin");secondAdmin=new SessionUser(other,key+"B",key,"admin");
    }
    @AfterEach void cleanup(){
        jdbc.update("DELETE FROM notification_read WHERE user_id IN (?,?)",owner,other);
        jdbc.update("DELETE FROM user_feedback WHERE user_id IN (?,?)",owner,other);
        jdbc.update("DELETE FROM bill WHERE user_id IN (?,?)",owner,other);
        jdbc.update("DELETE FROM sys_user WHERE id IN (?,?)",owner,other);
    }
    long feedback(long who,String reply){
        jdbc.update("INSERT INTO user_feedback(user_id,subject,content,reply,replied_at) VALUES(?,?,?, ?,NOW())",who,key,"水压偏低",reply);
        return jdbc.queryForObject("SELECT MAX(id) FROM user_feedback WHERE user_id=?",Long.class,who);
    }
    NotificationService.Message message(SessionUser who,long id){return service.inbox(who).items().stream().filter(m->m.category().equals("feedback")&&m.sourceId()==id).findFirst().orElseThrow();}
    @Test void ownRepliesOnlyAndCannotMarkAnotherUsersMessage(){
        long mine=feedback(owner,"已安排巡检"),theirs=feedback(other,"另一户回复");
        assertThat(service.inbox(user).items()).extracting(NotificationService.Message::sourceId).contains(mine).doesNotContain(theirs);
        var foreign=message(new SessionUser(other,key,"","user"),theirs);
        assertThat(service.mark(user,List.of(foreign.key()),true)).isZero();
        assertThat(message(new SessionUser(other,key,"","user"),theirs).read()).isFalse();
    }
    @Test void readingIsPersistentIdempotentAndIndependentOfBusinessAndOtherAdmins(){
        long id=feedback(owner,null);var first=message(admin,id);
        service.mark(admin,List.of(first.key()),true);service.mark(admin,List.of(first.key()),true);
        assertThat(message(admin,id).read()).isTrue();assertThat(message(secondAdmin,id).read()).isFalse();
        assertThat(jdbc.queryForObject("SELECT status FROM user_feedback WHERE id=?",String.class,id)).isEqualTo("pending");
        service.mark(admin,List.of(first.key()),false);assertThat(message(admin,id).read()).isFalse();
    }
    @Test void revisedReplyBecomesUnreadAndStaleBatchDoesNotReadIt(){
        long id=feedback(owner,"正在检查");var first=message(user,id);
        service.mark(user,List.of(first.key()),true);
        jdbc.update("UPDATE user_feedback SET reply='已修复，请确认' WHERE id=?",id);
        assertThat(message(user,id).key()).isNotEqualTo(first.key());
        assertThat(service.mark(user,List.of(first.key()),true)).isZero();
        assertThat(message(user,id).read()).isFalse();
    }
    @Test void readSnapshotLeavesNewMessagesUnreadAndResolvedPendingDisappears(){
        long id=feedback(owner,null);var first=message(admin,id);long fresh=feedback(owner,null);
        service.mark(admin,List.of(first.key()),true);assertThat(message(admin,fresh).read()).isFalse();
        jdbc.update("UPDATE user_feedback SET status='resolved' WHERE id=?",id);
        assertThat(service.inbox(admin).items()).noneMatch(m->m.category().equals("feedback")&&m.sourceId()==id);
    }
    @Test void outstandingBillsAreScopedIncludePartialPaymentAndDisappearAfterSettlement(){
        jdbc.update("INSERT INTO bill(bill_no,user_id,bill_period,total_amount,paid_amount,status,due_date) VALUES(?,?,'2026-09',100,40,3,NOW()),(?,?,'2026-09',100,0,0,NOW())",key,owner,key+"B",other);
        var messages=service.inbox(user).items();assertThat(messages).hasSize(1);
        assertThat(messages.get(0).content()).contains("60.00");
        jdbc.update("UPDATE bill SET paid_amount=total_amount,status=1 WHERE user_id=?",owner);
        assertThat(service.inbox(user).items()).isEmpty();
    }
    @Test void capIsExplicitAndUnreadCountMatchesReturnedScope(){
        for(int i=0;i<102;i++)feedback(owner,"回复 "+i);
        var inbox=service.inbox(user);assertThat(inbox.items()).hasSize(100);assertThat(inbox.truncated()).isTrue();
        assertThat(inbox.unreadCount()).isEqualTo(100);
        service.mark(user,inbox.items().stream().map(NotificationService.Message::key).toList(),true);
        assertThat(service.inbox(user).unreadCount()).isZero();
    }
}
