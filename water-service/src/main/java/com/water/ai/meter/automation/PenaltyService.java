package com.water.ai.meter.automation;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import static com.water.ai.meter.automation.CollectionService.*;

@Service
public class PenaltyService {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    public PenaltyService(JdbcTemplate jdbc,PlatformTransactionManager manager) {
        this.jdbc=jdbc; tx=new TransactionTemplate(manager);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }
    public record PolicyInput(boolean enabled,int graceDays,BigDecimal dailyRate,BigDecimal capRatio,LocalDate effectiveFrom) {}
    public Map<String,Object> policy() {
        var result=new LinkedHashMap<>(jdbc.queryForMap("SELECT * FROM penalty_policy ORDER BY effective_from DESC,id DESC LIMIT 1"));
        result.put("installationDate",jdbc.queryForObject("SELECT MIN(effective_from) FROM penalty_policy",LocalDate.class));
        result.put("basis","按昨日已结束自然日的未付本金计费，缴费优先抵本金；不复利；无历史流水的旧缴费保守视为已付；结清不重开");
        return result;
    }
    public void savePolicy(PolicyInput p) {
        if (p.graceDays()<0 || p.graceDays()>365 || p.dailyRate()==null || p.dailyRate().scale()>6
                || p.dailyRate().signum()<0 || p.dailyRate().compareTo(new BigDecimal("0.05"))>0
                || p.capRatio()==null || p.capRatio().scale()>6 || p.capRatio().signum()<0 || p.capRatio().compareTo(BigDecimal.ONE)>0
                || p.effectiveFrom()==null || p.effectiveFrom().isBefore(LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"))) || p.effectiveFrom().isAfter(LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).plusYears(1)))
            throw new IllegalArgumentException("违约金参数无效：宽限0至365天，日费率0至0.05，封顶0至1，最多六位小数；生效日不能早于今天");
        jdbc.update("INSERT INTO penalty_policy(enabled,grace_days,daily_rate,cap_ratio,effective_from) VALUES (?,?,?,?,?)",p.enabled(),p.graceDays(),p.dailyRate(),p.capRatio(),p.effectiveFrom());
    }
    public List<Map<String,Object>> ledger() {
        return jdbc.queryForList("SELECT l.*,b.bill_no FROM penalty_ledger l JOIN bill b ON b.id=l.bill_id ORDER BY l.id DESC LIMIT 200");
    }
    public int accrue() {
        List<Long> ids=jdbc.queryForList("SELECT id FROM bill WHERE deleted=0 AND status IN (0,2,3) AND total_amount>paid_amount AND due_date<CURRENT_DATE ORDER BY id",Long.class);
        int days=0;
        for(long id:ids) days+=accrueBill(id,LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")));
        return days;
    }
    // Clock argument is package-private for deterministic integration tests; API always uses the server date.
    int accrueBill(long billId,LocalDate today) {
        return tx.execute(status -> {
            var rows=jdbc.queryForList("SELECT * FROM bill WHERE id=? AND deleted=0 FOR UPDATE",billId);
            if(rows.isEmpty()) return 0;
            var bill=rows.get(0);
            if (!Set.of(0L,2L,3L).contains(number(bill,"status")) || decimal(bill,"paid_amount").compareTo(decimal(bill,"total_amount"))>=0 || bill.get("due_date")==null) return 0;
            LocalDate install=jdbc.queryForObject("SELECT MIN(effective_from) FROM penalty_policy",LocalDate.class);
            LocalDate due=time(bill.get("due_date")).toLocalDate();
            LocalDate first=due.plusDays(1).isAfter(install)?due.plusDays(1):install;
            LocalDate last=jdbc.queryForObject("SELECT MAX(accrual_date) FROM penalty_ledger WHERE bill_id=?",LocalDate.class,billId);
            if(last!=null && !last.isBefore(first)) first=last.plusDays(1);
            // Payments and penalties share the bill row lock. Read committed avoids a pre-lock snapshot.
            BigDecimal allPayments=jdbc.queryForObject("SELECT COALESCE(SUM(amount),0) FROM bill_payment WHERE bill_id=?",BigDecimal.class,billId);
            BigDecimal legacyPaid=decimal(bill,"paid_amount").subtract(allPayments).max(BigDecimal.ZERO);
            BigDecimal principal=decimal(bill,"total_amount").subtract(decimal(bill,"penalty")).max(BigDecimal.ZERO);
            BigDecimal accumulated=decimal(bill,"penalty");
            BigDecimal added=BigDecimal.ZERO;
            int days=0;
            // Bound one transaction; the next invocation resumes from the unique daily ledger.
            for(LocalDate day=first; day.isBefore(today) && days<366; day=day.plusDays(1)) {
                var policies=jdbc.queryForList("SELECT * FROM penalty_policy WHERE effective_from<=? ORDER BY effective_from DESC,id DESC LIMIT 1",day);
                if(policies.isEmpty()) continue;
                var p=policies.get(0);
                BigDecimal paidAtEnd=legacyPaid.add(jdbc.queryForObject("SELECT COALESCE(SUM(amount),0) FROM bill_payment WHERE bill_id=? AND paid_time<?",BigDecimal.class,billId,day.plusDays(1).atStartOfDay()));
                BigDecimal outstanding=principal.subtract(paidAtEnd).max(BigDecimal.ZERO);
                BigDecimal amount=BigDecimal.ZERO.setScale(2);
                if(flag(p.get("enabled")) && day.isAfter(due.plusDays(number(p,"grace_days"))))
                    amount=dailyAmount(principal,paidAtEnd,accumulated,decimal(p,"daily_rate"),decimal(p,"cap_ratio"));
                // DECIMAL(12,2) boundary: don't overflow an otherwise valid bill.
                amount=amount.min(new BigDecimal("9999999999.99").subtract(decimal(bill,"total_amount")).subtract(added).max(BigDecimal.ZERO));
                jdbc.update("INSERT INTO penalty_ledger(bill_id,accrual_date,policy_id,principal_outstanding,amount) VALUES (?,?,?,?,?)",billId,day,p.get("id"),outstanding,amount);
                accumulated=accumulated.add(amount); added=added.add(amount); days++;
            }
            if(due.isBefore(today)) jdbc.update("UPDATE bill SET penalty=COALESCE(penalty,0)+?,total_amount=total_amount+?,status=CASE WHEN COALESCE(paid_amount,0)>0 THEN 3 ELSE 2 END WHERE id=?",added,added,billId);
            return days;
        });
    }
    static BigDecimal dailyAmount(BigDecimal principal,BigDecimal paid,BigDecimal existingPenalty,BigDecimal rate,BigDecimal capRatio) {
        BigDecimal cap=principal.multiply(capRatio).setScale(2,RoundingMode.HALF_UP).subtract(existingPenalty).max(BigDecimal.ZERO);
        return principal.subtract(paid).max(BigDecimal.ZERO).multiply(rate).setScale(2,RoundingMode.HALF_UP).min(cap).setScale(2,RoundingMode.HALF_UP);
    }
}
