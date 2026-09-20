package com.water.ai.meter.assistant;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.*;
import java.sql.Timestamp;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.*;

/** Read-only, evidence-based business answers. No generated or simulated financial data. */
@Service
public class AssistantService {
    private static final ZoneId ZONE=ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter TIME=DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final AssistantRepository repository;
    private final Clock clock;
    @Autowired(required=false) private com.water.ai.meter.diagnosis.DiagnosisService diagnosis;
    @Autowired(required=false) private com.water.ai.meter.diagnosis.WorkOrderVerificationService verification;
    @Autowired public AssistantService(AssistantRepository repository) { this(repository,Clock.system(ZONE)); }
    AssistantService(AssistantRepository repository,Clock clock) { this.repository=repository; this.clock=clock; }

    @Transactional(readOnly=true)
    public Map<String,Object> answer(String question) {
        if(question==null || question.isBlank() || question.length()>500)
            throw new IllegalArgumentException("请输入1至500字的问题");
        String q=question.trim();
        LocalDateTime now=LocalDateTime.ofInstant(clock.instant(),ZONE).withNano(0);
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("question",q); result.put("source","business-data"); result.put("asOf",TIME.format(now));
        result.put("scope","全部区域"); result.put("status","answered"); result.put("rows",List.of());
        result.put("metrics",List.of()); result.put("evidence",List.of()); result.put("caveats",List.of()); result.put("links",List.of());
        var diagnosisQuestion=Pattern.compile("(?:查询|查看)?诊断事件\\s*#?\\s*([0-9]{1,15})\\s*(?:的)?(?:依据|复测进度|处理进度)[？?]?").matcher(q);
        if(diagnosisQuestion.matches()&&diagnosis!=null){
            long id=Long.parseLong(diagnosisQuestion.group(1));var c=diagnosis.detail(id,null);
            result.put("intent","diagnosis");result.put("scope","诊断事件 #"+id);result.put("summary",c.get("summary"));
            var probes=(List<Map<String,Object>>)c.get("probes");var evidence=(List<Map<String,Object>>)c.get("evidence");
            result.put("metrics",List.of(metric("证据记录",evidence.size()+" 条"),metric("复测成功",probes.stream().filter(p->"success".equals(p.get("state"))).count()+" 次")));
            result.put("evidence",List.of("事件来自水表 "+c.get("meter_no")+"；策略版本 "+c.get("policy_version")+"。", "诊断证据与复测请求独立留痕，可在事件详情核对报文、历史范围和失败原因。"));
            result.put("caveats",List.of("当前复测使用模拟采集器；模型评分不是漏水概率。助手不会创建工单或变更策略。"));
            result.put("links",List.of(link("查看诊断证据","/diagnosis?focus="+id)));return result;
        }
        var verificationQuestion=Pattern.compile("(?:查询|查看)?工单\\s*#?\\s*([0-9]{1,15})\\s*(?:的)?核验结果[？?]?").matcher(q);
        if(verificationQuestion.matches()&&verification!=null){
            long id=Long.parseLong(verificationQuestion.group(1));var rows=verification.list(id);result.put("intent","verification");result.put("scope","工单 #"+id);
            var names=Map.of("observing","仍在观测", "recovered","观测已恢复", "persistent","异常仍持续", "inconclusive","数据不足，无法判断");
            result.put("summary",rows.isEmpty()?"该工单暂无独立效果核验记录，请核对工单编号及完成状态。":"最新处理效果："+names.getOrDefault(Objects.toString(rows.get(0).get("state")),"待核查")+"。");
            result.put("caveats",List.of("核验采用独立观测记录，不改变原工单的完成状态；缺失数据不会被当作恢复。"));
            result.put("links",List.of(link("查看工单核验","/anomaly/workorder?focus="+id)));return result;
        }
        boolean inspection=has(q,"巡检","检修","健康","维修");
        boolean leak=has(q,"漏水","漏损","夜间用水","夜间流量");
        boolean revenue=has(q,"收入","实收","收款");
        boolean unpaid=has(q,"欠费","未缴","未付","催缴");
        if((inspection?1:0)+(leak?1:0)+(revenue?1:0)+(unpaid?1:0)!=1)
            return clarify(result,"请一次询问一个明确主题：优先巡检、区域漏水风险、本月登记收入或当前欠费。");
        if(has(q,"派单","创建工单","生成工单","调价","扣费","关阀","删除"))
            return clarify(result,"助手只提供查询和建议。请到对应业务页面核对对象后执行操作。");
        String temporal=revenue && has(q,"本月","这个月")?q.replace("上月同期",""):q;
        if(has(temporal,"昨天","昨日","去年","上周","本周","今年","上月","上个月","明天","未来","预测","最近","近7","近30","今天","今日","本年")
                || Pattern.compile("\\d{4}[-年/]|\\d+月|\\d+天").matcher(q).find())
            return clarify(result,"这里支持当前巡检/异常/欠费，以及本月收入与上月同期比较。指定其他日期请使用统计报表选择范围。");
        if(!revenue && has(q,"本月","这个月")) return clarify(result,"当前异常和欠费是此刻的状态，不代表本月发生量。按月统计请到统计报表选择日期。");
        var areas=repository.areas();
        Scope scope=scope(q,areas);
        if(scope.error!=null) return clarify(result,scope.error);
        if(!understood(q,areas))
            return clarify(result,"暂不能解析问题中的对象或筛选条件。请使用下方示例，并只添加一个已建档区域或水表编号；个人、用户类型、自定义阈值等筛选暂不支持。");
        result.put("scope",scope.label);
        if((revenue || unpaid) && (!scope.ids.isEmpty() || scope.meterNo!=null))
            return clarify(result,"收入与欠费问答目前仅支持全部区域。请移除区域/表号限定，或到业务列表核查指定对象。");
        if(inspection) inspect(result,scope,now);
        else if(leak) leaks(result,scope);
        else if(revenue) revenue(result,now);
        else unpaid(result,now);
        return result;
    }

    private void inspect(Map<String,Object> result,Scope scope,LocalDateTime now) {
        result.put("intent","inspection");
        var meters=repository.meters(scope.ids,scope.meterNo);
        List<Map<String,Object>> ranked=new ArrayList<>();
        for(var m:meters) {
            List<String> reasons=new ArrayList<>(); int score=0;
            if(number(m,"status")==1) {score+=100;reasons.add("设备状态为故障");}
            if(number(m,"open_count")>0) {score+=number(m,"severe_count")>0?80:50;reasons.add("未处理异常 "+number(m,"open_count")+" 条，其中高/紧急 "+number(m,"severe_count")+" 条");}
            Integer battery=nullableInt(m.get("battery_level")),signal=nullableInt(m.get("signal_strength"));
            if(battery==null) {score+=10;reasons.add("电量数据缺失，需核实");}
            else if(battery<=20) {score+=30;reasons.add("电量偏低："+battery+"%");}
            if(signal==null) {score+=10;reasons.add("信号数据缺失，需核实");}
            else if(signal<=30) {score+=20;reasons.add("信号偏弱："+signal+"/100");}
            LocalDateTime last=date(m.get("last_reading_time"));
            if(last==null) {score+=10;reasons.add("尚无抄表时间记录");}
            else if(last.isAfter(now)) {score+=10;reasons.add("抄表时间晚于当前时间，需核对设备时钟");}
            else if(ChronoUnit.DAYS.between(last,now)>=7) {score+=10;reasons.add("已 "+ChronoUnit.DAYS.between(last,now)+" 天未更新抄表");}
            if(score==0) continue;
            Map<String,Object> row=new LinkedHashMap<>();
            row.put("meterNo",m.get("meter_no")); row.put("area",Objects.toString(m.get("area_name"),"未分区"));
            row.put("priority",score>=80?"优先处理":score>=30?"尽快核查":"例行核查");
            row.put("reasons",reasons); row.put("lastReadingAt",last==null?"暂无记录":TIME.format(last));
            row.put("nextStep",number(m,"status")==1?"核验故障并安排检修":number(m,"open_count")>0?"先核查异常记录与现场读数":"核对设备电量、通信及抄表记录");
            row.put("score",score);ranked.add(row);
        }
        ranked.sort(Comparator.<Map<String,Object>>comparingInt(r->(int)r.get("score")).reversed().thenComparing(r->r.get("meterNo").toString()));
        result.put("summary",meters.isEmpty()?"该范围内没有在役或故障水表，请核对区域和表号。":ranked.isEmpty()?"当前没有水表触发巡检规则，可按日常计划巡检。":"当前有 "+ranked.size()+" 块水表需要核查，以下按规则优先级列出前 "+Math.min(10,ranked.size())+" 块。");
        result.put("rows",ranked.stream().limit(10).toList());
        result.put("metrics",List.of(metric("纳入检查",meters.size()+" 块"),metric("触发规则",ranked.size()+" 块")));
        result.put("evidence",List.of("依据：当前设备状态、未处理异常、电量、信号及最后抄表时间；停用、更换和删除设备不纳入。","故障100分；高/紧急异常80分，其他异常50分；低电量30分；弱信号20分；缺失数据、时间异常或超过7天未更新各10分。按总分降序，同分按表号排序。"));
        result.put("caveats",List.of("这是规则优先级，不是故障概率。长期未更新应结合实际抄表周期核实，告警也不能代替现场结论。"));
        result.put("links",List.of(link("查看水表与异常地图","/twin/map"),link("查看工单","/anomaly/workorder")));
    }

    private void leaks(Map<String,Object> result,Scope scope) {
        result.put("intent","leak");
        var anomalies=repository.leakEvidence(scope.ids,scope.meterNo);
        result.put("summary",anomalies.isEmpty()?"该范围暂未查到未处理的漏水相关告警；这不能证明不存在漏水。":"该范围有 "+anomalies.size()+" 条未处理的漏水相关告警，需要现场核查，目前不能判定已经漏水。");
        result.put("evidence",anomalies.stream().limit(10).map(a->Objects.toString(a.get("meter_no"))+" · "+type(Objects.toString(a.get("anomaly_type")))+" · "+Objects.toString(a.get("anomaly_no"))+" · "+Objects.toString(a.get("detected_time"),"时间缺失")+"；"+Objects.toString(a.get("description"),"无补充描述")).toList());
        result.put("metrics",List.of(metric("未处理相关告警",anomalies.size()+" 条"),metric("涉及水表",anomalies.stream().map(a->a.get("id")).distinct().count()+" 块")));
        result.put("caveats",List.of("仅统计在役/故障水表上未处理或处理中的夜间用水、流量突增和漏水告警；最多展示10条。","缺少连续流量、阀门状态和现场核验时，不能区分真实用水与漏水。请先核实告警时间、夜间用水情况和现场读数，再决定是否派单。"));
        result.put("links",List.of(link("查看区域与异常地图","/twin/map"),link("查看工单","/anomaly/workorder")));
    }

    private void revenue(Map<String,Object> result,LocalDateTime now) {
        result.put("intent","revenue");
        LocalDateTime start=now.toLocalDate().withDayOfMonth(1).atStartOfDay();
        LocalDateTime previousStart=start.minusMonths(1),previousEnd=now.minusMonths(1);
        var current=repository.receipts(start,now);var previous=repository.receipts(previousStart,previousEnd);
        BigDecimal amount=decimal(current.get("amount")),baseline=decimal(previous.get("amount")),delta=amount.subtract(baseline);
        String summary;
        if(number(current,"count")==0 && number(previous,"count")==0) summary="两个对比时段都没有登记收款流水，无法判断本月收入是否下降。";
        else if(baseline.signum()==0) summary="本月已登记收款 "+money(amount)+" 元；上月同期为0，无法计算变化百分比。";
        else summary="本月已登记收款 "+money(amount)+" 元，较上月同期"+(delta.signum()<0?"减少 ":delta.signum()>0?"增加 ":"持平，差额 ")+money(delta.abs())+" 元"+(delta.signum()==0?"。":"（"+delta.abs().multiply(BigDecimal.valueOf(100)).divide(baseline,1,RoundingMode.HALF_UP)+"%）。");
        result.put("summary",summary);
        result.put("scope","全部区域；本月截至查询时刻 / 上月同日同时刻（月末按实际天数截断）");
        result.put("metrics",List.of(metric("本月登记收入",money(amount)+" 元"),metric("上月同期",money(baseline)+" 元"),metric("本月收款笔数",number(current,"count")+" 笔")));
        result.put("evidence",List.of("本期：["+TIME.format(start)+", "+TIME.format(now)+")。","对比期：["+TIME.format(previousStart)+", "+TIME.format(previousEnd)+")。","数据来源为收款登记流水，按实际登记时间汇总，包含部分付款。"));
        result.put("caveats",List.of("未迁移为收款流水的历史已付金额不计入此口径；无流水不等于实际无收入。","差额只能说明登记金额变化，不能据此认定用水下降、欠费增加或漏水。可在统计报表核对日期和收款记录。登记收入不是支付机构到账证明。"));
        result.put("links",List.of(link("查看缴费记录","/bill/payment"),link("打开统计报表","/report")));
    }

    private void unpaid(Map<String,Object> result,LocalDateTime now) {
        result.put("intent","unpaid"); var data=repository.unpaid(now);
        result.put("summary","当前有 "+number(data,"count")+" 笔未结清账单，待收余额 "+money(decimal(data.get("amount")))+" 元，其中已到期 "+number(data,"overdue")+" 笔。");
        result.put("metrics",List.of(metric("未结清账单",number(data,"count")+" 笔"),metric("待收余额",money(decimal(data.get("amount")))+" 元")));
        result.put("evidence",List.of("按未删除账单的应收金额减累计已付金额计算，包含部分付款；仅纳入余额大于0的账单。"));
        result.put("caveats",List.of("该金额是当前账单余额，不代表本月新增欠费；是否逾期按账单到期时间判断。"));
        result.put("links",List.of(link("核对账单","/bill/list")));
    }

    private record Scope(List<Long> ids,String meterNo,String label,String error) {}
    // Fail closed on unparsed qualifiers; keyword routing alone must not turn
    // "张三欠费多少" or "A区和Z区漏水" into an unfiltered business answer.
    private boolean understood(String question,List<Map<String,Object>> areas) {
        String rest=question.toUpperCase(Locale.ROOT).replaceAll("WM-[A-Z0-9-]+","");
        List<String> tokens=new ArrayList<>();
        for(var area:areas) {
            String name=Objects.toString(area.get("area_name")),code=Objects.toString(area.get("area_code"));
            tokens.add(name.toUpperCase(Locale.ROOT));tokens.add(name.split("[（(]",2)[0].toUpperCase(Locale.ROOT));tokens.add(code.toUpperCase(Locale.ROOT));
            if(code.matches("[A-Z]001")) tokens.add(code.charAt(0)+"区");
        }
        tokens.addAll(List.of("上月同期","相比","比较","对比","与","比","全部区域","所有区域","全区","为什么","是什么","是不是","有没有","怎么样","哪些","哪个","多少","是否","如何","怎么","几个","几块","几笔","请问","请","帮我","帮忙","查询","查看","分析","统计","一下","当前","目前","现在","本月","这个月","系统中","系统","水务","水表","设备","需要","优先级","优先","巡检","检修","维修","健康","状态","情况","名单","清单","漏水","漏损","夜间用水","夜间流量","风险","登记","收入","实收","收款","流水","水费","下降","减少","增加","上升","变化","原因","欠费","未缴","未付","催缴","账单","余额","金额","合计","总共","总计","总额","我们","我","全部","所有","存在","发生","的","了","有","需","是","吗","呢","吗","要"));
        tokens.sort(Comparator.comparingInt(String::length).reversed());
        for(String token:tokens) if(!token.isEmpty()) rest=rest.replace(token,"");
        return rest.replaceAll("[\\s，。？！、,.?!：:]","").isEmpty();
    }
    private Scope scope(String question,List<Map<String,Object>> areas) {
        String q=question.toUpperCase(Locale.ROOT);Set<Map<String,Object>> matches=new LinkedHashSet<>();
        Matcher meterMatcher=Pattern.compile("WM-[A-Z0-9-]+").matcher(q);String meter=null;
        while(meterMatcher.find()) {if(meter!=null) return new Scope(List.of(),null,"", "请一次指定一块水表。"); meter=meterMatcher.group();}
        if(meter!=null) q=q.replace(meter,"");
        for(var a:areas) {
            String name=Objects.toString(a.get("area_name")),code=Objects.toString(a.get("area_code")).toUpperCase(Locale.ROOT);
            String base=name.split("[（(]",2)[0];
            if(q.contains(name.toUpperCase(Locale.ROOT)) || (!base.isBlank() && q.contains(base.toUpperCase(Locale.ROOT)))
                    || Pattern.compile("(?<![A-Z0-9-])"+Pattern.quote(code)+"(?![A-Z0-9-])").matcher(q).find()
                    || (code.matches("[A-Z]001") && q.contains(code.charAt(0)+"区"))) matches.add(a);
        }
        if(matches.size()>1) return new Scope(List.of(),meter,"","请一次指定一个区域，避免混合不同区域的数据。");
        if(matches.isEmpty()) {
            String rest=q.replace("全部区域","").replace("所有区域","").replace("全区","");
            if(rest.contains("区") || rest.contains("街道") || Pattern.compile("[A-Z]\\d{3}").matcher(rest).find())
                return new Scope(List.of(),meter,"","未能匹配指定区域，请使用系统中的区域名称、编码，或A区/B区等示范别名。");
            return new Scope(List.of(),meter,meter==null?"全部区域":"水表 "+meter,null);
        }
        var selected=matches.iterator().next();Set<Long> ids=new LinkedHashSet<>();ids.add(((Number)selected.get("id")).longValue());
        boolean changed;
        do {changed=false;for(var a:areas) if(a.get("parent_id") instanceof Number parent && ids.contains(parent.longValue())) changed|=ids.add(((Number)a.get("id")).longValue());} while(changed);
        return new Scope(List.copyOf(ids),meter,selected.get("area_name")+"（含下级区域）"+(meter==null?"":" · "+meter),null);
    }
    private Map<String,Object> clarify(Map<String,Object> result,String message) {
        result.put("intent","help");result.put("status","clarification");result.put("summary",message);
        result.put("scope","尚未执行业务查询");
        result.put("evidence",List.of("可以问：哪些水表需要优先巡检？","可以问：A区是不是漏水？","可以问：本月登记收入有多少？","可以问：当前有多少欠费账单？"));
        result.put("caveats",List.of("当前为业务数据规则问答，暂不支持自由追问、预测或自动执行操作；请在每条问题中写明对象和主题。"));
        result.put("links",List.of(link("打开统计报表","/report")));return result;
    }
    private static boolean has(String q,String...words) {return Arrays.stream(words).anyMatch(q::contains);}
    private static long number(Map<String,Object> row,String key) {return row.get(key) instanceof Number n?n.longValue():0;}
    private static Integer nullableInt(Object value) {return value instanceof Number n?n.intValue():null;}
    private static BigDecimal decimal(Object v) {return v==null?BigDecimal.ZERO:new BigDecimal(v.toString());}
    private static String money(BigDecimal value) {return value.setScale(2,RoundingMode.HALF_UP).toPlainString();}
    private static LocalDateTime date(Object value) {return value instanceof Timestamp t?t.toLocalDateTime():value instanceof LocalDateTime t?t:null;}
    private static Map<String,String> metric(String label,String value) {return Map.of("label",label,"value",value);}
    private static Map<String,String> link(String label,String path) {return Map.of("label",label,"path",path);}
    private static String type(String value) {return switch(value) {case "night_usage"->"夜间用水异常";case "high_flow","sudden_increase"->"流量/用量突增";default->"疑似漏水";};}
}
