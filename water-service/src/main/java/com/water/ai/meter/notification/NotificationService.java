package com.water.ai.meter.notification;

import com.water.ai.meter.security.SessionUser;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class NotificationService {
    private final JdbcTemplate jdbc;
    private static final int CATEGORY_LIMIT=100;
    public record Message(String key,String category,long sourceId,String title,String content,String priority,
                          String occurredAt,String targetPath,boolean read) {
        Message withRead(boolean value){return new Message(key,category,sourceId,title,content,priority,occurredAt,targetPath,value);}
    }
    public record Inbox(List<Message> items,long unreadCount,boolean truncated,int perCategoryLimit,String refreshedAt) {}

    @Transactional(readOnly=true)
    public Inbox inbox(SessionUser user){
        List<Message> messages=new ArrayList<>();boolean truncated=false;
        var now=LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
        if(user.isAdmin()){
            truncated|=add(messages,"anomaly","/anomaly/list","""
                SELECT id,CONCAT('异常待处理 · ',anomaly_no) title,
                CONCAT('水表 ID：',COALESCE(meter_id,'未关联'),'；',IF(status=0,'待处理','处理中'),'。',COALESCE(description,'')) content,
                IF(severity IN ('critical','high'),'danger','warning') priority,
                COALESCE(update_time,detected_time,create_time) happened,CONCAT_WS('|',severity,status,update_time) revision
                FROM anomaly_record WHERE deleted=0 AND status IN (0,1) ORDER BY happened DESC,id DESC LIMIT 101
                """);
            truncated|=add(messages,"workorder","/anomaly/workorder","""
                SELECT id,CONCAT('工单待办 · ',order_no) title,
                CONCAT(COALESCE(title,''),'；',CASE status WHEN 0 THEN '待派单' WHEN 1 THEN '已派单' ELSE '处理中' END,
                '；处理人：',COALESCE(handler_name,'待分配'),'。',COALESCE(description,'')) content,
                IF(priority<=2,'danger','info') priority,COALESCE(update_time,create_time) happened,
                CONCAT_WS('|',status,update_time,handler_id) revision
                FROM work_order WHERE deleted=0 AND status IN (0,1,2) ORDER BY happened DESC,id DESC LIMIT 101
                """);
            truncated|=add(messages,"automation","/automation","""
                SELECT t.id,CONCAT('采集失败 · 水表 ',COALESCE(m.meter_no,t.meter_id)) title,
                CONCAT('任务 #',t.id,'；已尝试 ',t.attempts,' 次。',COALESCE(t.last_error,'请检查设备和采集计划')) content,
                'warning' priority,COALESCE(t.completed_at,t.next_attempt_at,r.created_at) happened,
                CONCAT_WS('|',t.status,t.attempts,t.last_error,t.completed_at) revision
                FROM automation_task t JOIN automation_run r ON r.id=t.run_id LEFT JOIN water_meter m ON m.id=t.meter_id
                WHERE t.status='failed' ORDER BY happened DESC,t.id DESC LIMIT 101
                """);
            truncated|=add(messages,"feedback","/feedback","""
                SELECT f.id,CONCAT('用户反馈 · ',f.subject) title,
                CONCAT(COALESCE(u.real_name,u.username,'用户'),'：',f.content,'\n处理进度：',
                IF(f.status='pending','待处理','处理中'),IF(f.reply IS NULL,'',CONCAT('\n最近回复：',f.reply))) content,
                'info' priority,COALESCE(f.replied_at,f.created_at) happened,CONCAT_WS('|',f.status,f.replied_at) revision
                FROM user_feedback f LEFT JOIN sys_user u ON u.id=f.user_id
                WHERE f.status IN ('pending','processing') ORDER BY happened DESC,f.id DESC LIMIT 101
                """);
        }else{
            truncated|=add(messages,"feedback","/portal#feedback","""
                SELECT id,CONCAT('反馈有回复 · ',subject) title,
                CONCAT('您的问题：',content,'\n管理员回复：',reply,'\n处理进度：',IF(status='resolved','已解决','处理中')) content,
                'info' priority,COALESCE(replied_at,created_at) happened,CONCAT_WS('|',status,replied_at) revision
                FROM user_feedback WHERE user_id=? AND reply IS NOT NULL AND reply<>'' ORDER BY happened DESC,id DESC LIMIT 101
                """,user.id());
        }
        String diagnosisSql="""
            SELECT c.id,CONCAT('诊断进度 · ',m.meter_no) title,
            CONCAT(c.summary,'；',CASE c.state WHEN 'candidate' THEN '影子候选' WHEN 'probing' THEN '复测中'
              WHEN 'needs_review' THEN '待人工核查' WHEN 'work_order_linked' THEN '已关联工单'
              WHEN 'awaiting_verification' THEN '处理后观测中' ELSE '事件已关闭' END,
              CASE v.state WHEN 'recovered' THEN '；处理后观测已恢复' WHEN 'persistent' THEN '；异常仍持续'
                WHEN 'inconclusive' THEN '；核验数据不足' ELSE '' END) content,
            IF(c.severity='high','warning','info') priority,COALESCE(v.updated_at,c.last_evidence_at) happened,
            CONCAT_WS('|',c.state,c.severity,COALESCE(v.state,'')) revision
            FROM diagnosis_case c JOIN water_meter m ON m.id=c.meter_id
            LEFT JOIN work_order_verification v ON v.id=(SELECT MAX(v2.id) FROM work_order_verification v2 WHERE v2.case_id=c.id)
            WHERE m.deleted=0
            """;
        if(user.isAdmin())truncated|=add(messages,"diagnosis","/diagnosis",diagnosisSql+" ORDER BY happened DESC,c.id DESC LIMIT 101");
        else truncated|=add(messages,"diagnosis","/portal#diagnosis",diagnosisSql+" AND c.mode='assisted' AND c.user_id=? AND m.user_id=? ORDER BY happened DESC,c.id DESC LIMIT 101",user.id(),user.id());
        String billSql="""
            SELECT id,CONCAT(IF(due_date<?,'账单已逾期 · ','账单即将到期 · '),bill_no) title,
            CONCAT('账期：',bill_period,'；剩余应付 ¥',CAST(total_amount-COALESCE(paid_amount,0) AS CHAR),
            '；应付日期：',DATE_FORMAT(due_date,'%Y-%m-%d %H:%i'),'。请核对账单后处理。') content,
            IF(due_date<?,'warning','info') priority,COALESCE(update_time,create_time) happened,
            CONCAT_WS('|',status,due_date,total_amount,paid_amount) revision
            FROM bill WHERE deleted=0 AND total_amount>COALESCE(paid_amount,0) AND due_date<=?
            """;
        if(user.isAdmin())truncated|=add(messages,"bill","/bill/list",billSql+" ORDER BY happened DESC,id DESC LIMIT 101",now,now,now.plusDays(7));
        else truncated|=add(messages,"bill","/portal#bills",billSql+" AND user_id=? ORDER BY happened DESC,id DESC LIMIT 101",now,now,now.plusDays(7),user.id());

        Set<String> readKeys=new HashSet<>();
        if(!messages.isEmpty()){
            List<Object> args=new ArrayList<>();args.add(user.id());messages.forEach(m->args.add(m.key()));
            readKeys.addAll(jdbc.queryForList("SELECT message_key FROM notification_read WHERE user_id=? AND message_key IN ("+
                    String.join(",",Collections.nCopies(messages.size(),"?"))+")",String.class,args.toArray()));
        }
        var items=messages.stream().map(m->m.withRead(readKeys.contains(m.key())))
                .sorted(Comparator.comparing(Message::occurredAt).reversed().thenComparing(Message::key)).toList();
        return new Inbox(items,items.stream().filter(m->!m.read()).count(),truncated,CATEGORY_LIMIT,now.toString());
    }
    private boolean add(List<Message> out,String category,String path,String sql,Object...args){
        var rows=jdbc.queryForList(sql,args);
        rows.stream().limit(CATEGORY_LIMIT).forEach(row->{
            long id=((Number)row.get("id")).longValue();String title=text(row,"title"),body=text(row,"content");
            String key=digest(category+"\u0000"+id+"\u0000"+title+"\u0000"+body+"\u0000"+text(row,"revision"));
            out.add(new Message(key,category,id,title,body,text(row,"priority"),text(row,"happened"),path,false));
        });
        return rows.size()>CATEGORY_LIMIT;
    }
    private static String text(Map<String,Object> row,String key){return Objects.toString(row.get(key),"");}
    private static String digest(String value){
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    @Transactional
    public int mark(SessionUser user,List<String> keys,boolean read){
        if(keys==null||keys.isEmpty()||keys.size()>1000||keys.stream().anyMatch(k->k==null||!k.matches("[a-f0-9]{64}")))
            throw new IllegalArgumentException("请选择 1 至 1000 条有效消息");
        Set<String> visible=new HashSet<>();inbox(user).items().forEach(m->visible.add(m.key()));
        var allowed=keys.stream().distinct().filter(visible::contains).toList();
        for(String key:allowed){
            if(read)jdbc.update("INSERT INTO notification_read(user_id,message_key) VALUES(?,?) ON DUPLICATE KEY UPDATE read_at=read_at",user.id(),key);
            else jdbc.update("DELETE FROM notification_read WHERE user_id=? AND message_key=?",user.id(),key);
        }
        return allowed.size();
    }
}
