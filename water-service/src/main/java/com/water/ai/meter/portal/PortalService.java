package com.water.ai.meter.portal;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.water.ai.meter.entity.*;
import com.water.ai.meter.mapper.*;
import com.water.ai.meter.security.SessionUser;
import com.water.ai.meter.service.BillService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PortalService {
    private final BillMapper bills;
    private final BillPaymentMapper payments;
    private final WaterMeterMapper meters;
    private final MeterReadingMapper readings;
    private final BillService billing;
    private final JdbcTemplate jdbc;
    private static final String FEEDBACK_COLUMNS="f.id,f.meter_id AS meterId,f.subject,f.content,f.status,f.reply,f.created_at AS createdAt,f.replied_at AS repliedAt";

    @Transactional(readOnly=true)
    public Map<String,Object> overview(SessionUser user) {
        var ownBills=bills.selectByUserId(user.id());
        List<Map<String,Object>> reminders=new ArrayList<>();
        LocalDateTime now=LocalDateTime.now(java.time.ZoneId.of("Asia/Shanghai"));
        for(Bill bill:ownBills) {
            BigDecimal left=bill.getTotalAmount().subtract(bill.getPaidAmount()==null?BigDecimal.ZERO:bill.getPaidAmount());
            if(left.signum()>0 && bill.getDueDate()!=null && bill.getDueDate().isBefore(now.plusDays(7))) {
                reminders.add(Map.of("billId",bill.getId(),"billNo",bill.getBillNo(),"remainingAmount",left,"dueDate",bill.getDueDate(),"overdue",bill.getDueDate().isBefore(now)));
            }
        }
        return Map.of("user",user,"meters",meters.selectList(new LambdaQueryWrapper<WaterMeter>().eq(WaterMeter::getUserId,user.id())),
                "readings",readings.selectList(new LambdaQueryWrapper<MeterReading>().eq(MeterReading::getUserId,user.id()).orderByDesc(MeterReading::getReadingTime).last("LIMIT 200")),
                "bills",ownBills,"reminders",reminders,"feedback",feedback(user.id()));
    }
    public Bill ownBill(long userId,long billId) { return requireOwner(bills.selectById(billId),userId); }
    private Bill requireOwner(Bill bill,long userId) {
        if(bill==null || !Objects.equals(bill.getUserId(),userId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"账单不存在或无权访问");
        return bill;
    }
    public List<BillPayment> history(long userId,long billId) { ownBill(userId,billId);return payments.selectByBillId(billId); }
    @Transactional
    public Map<String,Object> pay(long userId,long billId,BigDecimal amount,String method,String requestId) {
        requireOwner(bills.selectForUpdate(billId),userId);
        return billing.payBill(billId,amount,method,requestId);
    }
    public List<Map<String,Object>> feedback(long userId) {
        return jdbc.queryForList("SELECT "+FEEDBACK_COLUMNS+" FROM user_feedback f WHERE f.user_id=? ORDER BY f.id DESC",userId);
    }
    @Transactional
    public Map<String,Object> createFeedback(long userId,Long meterId,String subject,String content) {
        subject=requiredText(subject,100,"标题");content=requiredText(content,2000,"反馈内容");
        if(meterId!=null) {
            WaterMeter meter=meters.selectById(meterId);
            if(meter==null || !Objects.equals(meter.getUserId(),userId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"水表不存在或无权访问");
        }
        org.springframework.jdbc.support.GeneratedKeyHolder key=new org.springframework.jdbc.support.GeneratedKeyHolder();
        final String finalSubject=subject, finalContent=content;
        jdbc.update(connection->{
            var stmt=connection.prepareStatement("INSERT INTO user_feedback(user_id,meter_id,subject,content) VALUES (?,?,?,?)",java.sql.Statement.RETURN_GENERATED_KEYS);
            stmt.setLong(1,userId);stmt.setObject(2,meterId);stmt.setString(3,finalSubject);stmt.setString(4,finalContent);return stmt;
        },key);
        return Map.of("id",Objects.requireNonNull(key.getKey()).longValue(),"status","pending");
    }
    public List<Map<String,Object>> adminFeedback() {
        return jdbc.queryForList("SELECT "+FEEDBACK_COLUMNS+",u.username,u.real_name AS realName FROM user_feedback f LEFT JOIN sys_user u ON u.id=f.user_id ORDER BY f.id DESC LIMIT 1000");
    }
    public Map<String,Object> adminFeedbackDetail(long id) {
        var rows=jdbc.queryForList("SELECT "+FEEDBACK_COLUMNS+",u.username,u.real_name AS realName FROM user_feedback f LEFT JOIN sys_user u ON u.id=f.user_id WHERE f.id=?",id);
        if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"反馈不存在");
        return rows.get(0);
    }
    public void reply(long id,long adminId,String reply,String status) {
        reply=requiredText(reply,2000,"处理回复");
        if(!Set.of("processing","resolved").contains(Objects.toString(status,""))) throw new IllegalArgumentException("处理状态无效");
        if(jdbc.update("UPDATE user_feedback SET reply=?,status=?,replied_by=?,replied_at=NOW() WHERE id=?",reply,status,adminId,id)!=1)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"反馈不存在");
    }
    private String requiredText(String text,int limit,String name) {
        if(text==null || text.isBlank() || text.trim().length()>limit) throw new IllegalArgumentException(name+"不能为空，最多 "+limit+" 字");
        return text.trim();
    }
}
