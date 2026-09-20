package com.water.ai.meter.tariff;

import com.water.ai.meter.entity.MeterReading;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import java.math.BigDecimal;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

@Service
public class ResidentialTariffService {
    private final JdbcTemplate jdbc;
    public ResidentialTariffService(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public record AccountInput(String name,List<Long> meterIds,String profileKey,LocalDate effectiveFrom,BigDecimal openingUsage,String openingNote) {}
    public record Prepared(long accountId,int year,BigDecimal before,BigDecimal after,LocalDateTime readAt,AnnualTariffCalculator.Quote quote,Map<String,Object> detail) {}

    private void lockMeter(long id){
        // Take an exclusive lock directly. INSERT IGNORE first takes a shared lock
        // on an existing key, which can deadlock when two callers upgrade it.
        jdbc.update("INSERT INTO tariff_meter_guard(meter_id) VALUES(?) ON DUPLICATE KEY UPDATE meter_id=VALUES(meter_id)",id);
        jdbc.queryForObject("SELECT meter_id FROM tariff_meter_guard WHERE meter_id=? FOR UPDATE",Long.class,id);
    }
    @Transactional(propagation=Propagation.MANDATORY) public void lockForBilling(long meterId){
        lockMeter(meterId);
        var rows=jdbc.queryForList("SELECT account_id FROM tariff_account_meter WHERE meter_id=? FOR UPDATE",meterId);
        if(!rows.isEmpty())jdbc.queryForMap("SELECT id FROM tariff_account WHERE id=? FOR UPDATE",rows.get(0).get("account_id"));
    }
    public List<Map<String,Object>> accounts(){
        return jdbc.queryForList("SELECT a.*,u.username,(SELECT GROUP_CONCAT(m.meter_no ORDER BY m.id SEPARATOR ', ') FROM tariff_account_meter am JOIN water_meter m ON m.id=am.meter_id WHERE am.account_id=a.id) meters FROM tariff_account a JOIN sys_user u ON u.id=a.user_id ORDER BY a.id DESC LIMIT 200");
    }
    public Map<String,Object> detail(long id){
        var rows=jdbc.queryForList("SELECT * FROM tariff_account WHERE id=?",id);
        if(rows.isEmpty())throw new IllegalArgumentException("结算户不存在");
        return Map.of("account",rows.get(0),"years",jdbc.queryForList("SELECT * FROM tariff_year_balance WHERE account_id=? ORDER BY billing_year DESC",id));
    }
    public List<Map<String,Object>> eligible(String search){
        return jdbc.queryForList("SELECT m.id,m.meter_no,m.user_id,u.username,u.real_name,m.current_reading,m.last_reading_time FROM water_meter m JOIN sys_user u ON u.id=m.user_id WHERE m.deleted=0 AND m.status IN(0,1) AND u.deleted=0 AND u.status=0 AND u.user_type='residential' AND NOT EXISTS(SELECT 1 FROM tariff_account_meter am WHERE am.meter_id=m.id) AND m.meter_no LIKE ? ORDER BY m.id LIMIT 100", "%"+Objects.toString(search,"").trim()+"%");
    }
    @Transactional public long create(AccountInput input){
        if(input==null||input.name()==null||input.name().isBlank()||input.name().length()>100||input.meterIds()==null||input.meterIds().isEmpty()||input.meterIds().size()>50||input.meterIds().stream().anyMatch(Objects::isNull)||input.effectiveFrom()==null||input.openingNote()==null||input.openingNote().isBlank()||input.openingNote().length()>500)throw new IllegalArgumentException("请填写结算户、1至50块水表、生效日和期初用量依据");
        AnnualTariffCalculator.profile(input.profileKey());AnnualTariffCalculator.validUsage(input.openingUsage());
        LocalDate today=LocalDate.now(ZoneId.of("Asia/Shanghai"));
        if(input.effectiveFrom().isBefore(today.withDayOfYear(1))||input.effectiveFrom().isAfter(today.plusYears(1)))throw new IllegalArgumentException("生效日须在今年1月1日至一年后之间");
        List<Long> ids=input.meterIds().stream().distinct().sorted().toList();Long owner=null;
        for(long id:ids)lockMeter(id);
        for(long id:ids){
            var rows=jdbc.queryForList("SELECT m.user_id FROM water_meter m JOIN sys_user u ON u.id=m.user_id WHERE m.id=? AND m.deleted=0 AND m.status IN(0,1) AND u.deleted=0 AND u.status=0 AND u.user_type='residential'",id);
            if(rows.isEmpty())throw new IllegalArgumentException("水表不存在、不可用或不是居民用水");
            long user=((Number)rows.get(0).get("user_id")).longValue();if(owner!=null&&owner!=user)throw new IllegalArgumentException("同一结算户的水表须归属同一用户");owner=user;
            if(jdbc.queryForObject("SELECT COUNT(*) FROM tariff_account_meter WHERE meter_id=?",Integer.class,id)>0)throw new IllegalArgumentException("水表已经绑定结算户，不能重复绑定");
            if(jdbc.queryForObject("SELECT COUNT(*) FROM bill b LEFT JOIN meter_reading r ON r.id=b.reading_id WHERE b.meter_id=? AND (r.reading_time>=? OR b.bill_period>=?)",Integer.class,id,input.effectiveFrom().atStartOfDay(),input.effectiveFrom().toString().substring(0,7))>0)
                throw new IllegalArgumentException("生效账期已有账单，请选择后续未出账账期并核对期初累计量");
        }
        final long user=owner;var key=new GeneratedKeyHolder();
        jdbc.update(c->{var s=c.prepareStatement("INSERT INTO tariff_account(name,user_id,profile_key,effective_from,opening_usage,opening_note) VALUES(?,?,?,?,?,?)",Statement.RETURN_GENERATED_KEYS);s.setString(1,input.name().trim());s.setLong(2,user);s.setString(3,input.profileKey());s.setObject(4,input.effectiveFrom());s.setBigDecimal(5,input.openingUsage());s.setString(6,input.openingNote().trim());return s;},key);
        long account=Objects.requireNonNull(key.getKey()).longValue();
        for(long id:ids)jdbc.update("INSERT INTO tariff_account_meter(meter_id,account_id) VALUES(?,?)",id,account);
        jdbc.update("INSERT INTO tariff_year_balance(account_id,billing_year,used_amount) VALUES(?,?,?)",account,input.effectiveFrom().getYear(),input.openingUsage());
        return account;
    }

    /** Participates in the same transaction as the bill, including legacy-mode selection. */
    @Transactional(propagation=Propagation.MANDATORY) public Prepared prepare(long meterId,long userId,String userType,MeterReading reading){
        lockMeter(meterId);
        var mappings=jdbc.queryForList("SELECT account_id FROM tariff_account_meter WHERE meter_id=? FOR UPDATE",meterId);
        if(mappings.isEmpty())return null;
        long account=((Number)mappings.get(0).get("account_id")).longValue();
        var a=jdbc.queryForMap("SELECT * FROM tariff_account WHERE id=? FOR UPDATE",account);
        if(((Number)a.get("user_id")).longValue()!=userId||!"residential".equals(userType))throw new IllegalArgumentException("结算户归属或用水性质发生变化，请核查档案");
        LocalDate effective=((java.sql.Date)a.get("effective_from")).toLocalDate();LocalDateTime at=reading.getReadingTime();
        if(at==null||at.toLocalDate().isBefore(effective))throw new IllegalArgumentException("抄表早于结算户生效日期，不能自动使用新水价");
        if(reading.getReadingPeriod()!=null&&!reading.getReadingPeriod().isBlank()&&!reading.getReadingPeriod().equals(at.toString().substring(0,7)))throw new IllegalArgumentException("抄表账期与时间不一致");
        if(at.isAfter(LocalDateTime.now(ZoneId.of("Asia/Shanghai"))))throw new IllegalArgumentException("未来抄表不能提前占用年度额度");
        int year=at.getYear();
        LocalDateTime previous=jdbc.queryForObject("SELECT MAX(reading_time) FROM meter_reading WHERE meter_id=? AND id<>? AND deleted=0 AND status=1 AND reading_time<?",LocalDateTime.class,meterId,reading.getId(),at);
        if(previous!=null&&previous.getYear()!=year&&!previous.equals(LocalDate.of(year,1,1).atStartOfDay().minusSeconds(1)))throw new IllegalArgumentException("用量区间跨年，请先补齐上一年末23:59:59的分界读数，不能自动估算拆量");
        jdbc.update("INSERT IGNORE INTO tariff_year_balance(account_id,billing_year,used_amount) VALUES(?,?,0)",account,year);
        var balance=jdbc.queryForMap("SELECT * FROM tariff_year_balance WHERE account_id=? AND billing_year=? FOR UPDATE",account,year);
        Object last=balance.get("last_read_at");
        if(last!=null && at.isBefore(last instanceof Timestamp t?t.toLocalDateTime():(LocalDateTime)last))throw new IllegalArgumentException("本年度存在时间更晚的已结算抄表，迟到记录需人工核查，不能倒序计费");
        BigDecimal before=new BigDecimal(balance.get("used_amount").toString()),after=before.add(reading.getUsageAmount());
        String profile=(String)a.get("profile_key");var q=AnnualTariffCalculator.calculate(profile,before,reading.getUsageAmount());var policy=AnnualTariffCalculator.profile(profile);
        Map<String,Object> detail=new LinkedHashMap<>();
        detail.put("schema","residential-annual-v1");detail.put("accountId",account);detail.put("accountName",a.get("name"));detail.put("profileKey",profile);detail.put("profileName",policy.name());detail.put("source",policy.source());
        detail.put("year",year);detail.put("beforeUsage",before);detail.put("afterUsage",after);detail.put("tiers",q.tiers());detail.put("sewageUnit",policy.sewageUnit());detail.put("sewageAmount",q.sewage());detail.put("usage",reading.getUsageAmount());
        detail.put("rounding","累计费用四舍五入后取差额，分次计价与合并计价保持一致");detail.put("openingUsage",year==effective.getYear()?a.get("opening_usage"):BigDecimal.ZERO);detail.put("effectiveFrom",effective.toString());
        return new Prepared(account,year,before,after,at,q,detail);
    }
    @Transactional(propagation=Propagation.MANDATORY) public void record(Prepared p,long bill,long reading){
        jdbc.update("INSERT INTO tariff_bill_snapshot(bill_id,reading_id,account_id,billing_year,before_usage,after_usage,profile_key) VALUES(?,?,?,?,?,?,?)",bill,reading,p.accountId(),p.year(),p.before(),p.after(),p.detail().get("profileKey"));
        jdbc.update("UPDATE tariff_year_balance SET used_amount=?,last_read_at=? WHERE account_id=? AND billing_year=?",p.after(),p.readAt(),p.accountId(),p.year());
    }
}
