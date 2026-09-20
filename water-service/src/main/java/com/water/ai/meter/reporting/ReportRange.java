package com.water.ai.meter.reporting;

import java.time.*;
import java.time.temporal.*;
import java.util.*;
import java.util.regex.*;

/** Small, deterministic command grammar. Unsupported filters must never be ignored. */
public record ReportRange(LocalDate startDate, LocalDate endDate, Granularity granularity) {
    public enum Granularity { DAY, WEEK, MONTH, YEAR }
    public record Bucket(LocalDate startDate, LocalDate endDate) {}
    private static final String DATE = "\\d{4}-\\d{2}-\\d{2}";
    private static final Pattern COMMAND = Pattern.compile("(?:请)?(?:帮我)?(?:生成|查询|统计)?(" + DATE + "(?:至|到|~)" + DATE + "|今天|昨日|昨天|本周|上周|本月|上月|本年|今年|去年)(?:按([日周月年]))?(?:综合|用水量|收入|故障率)?(?:统计)?(?:报表)?");

    public ReportRange {
        if(startDate == null || endDate == null || granularity == null) throw new IllegalArgumentException("请选择开始日期、结束日期和日/周/月/年粒度");
        if(startDate.isAfter(endDate)) throw new IllegalArgumentException("开始日期不能晚于结束日期");
        long days=ChronoUnit.DAYS.between(startDate,endDate)+1;
        if(startDate.getYear()<1900 || endDate.getYear()>2100 || days>(granularity==Granularity.DAY ? 366 : 3660))
            throw new IllegalArgumentException("日期限1900至2100年；日报最多366天，其他粒度最多3660天");
    }

    public static ReportRange parse(String command, Clock clock) {
        if(command==null || command.length()>200) throw new IllegalArgumentException("请输入不超过200字的报表指令");
        String input=command.replaceAll("\\s+","").replace("，","").replace("。","");
        Matcher match=COMMAND.matcher(input);
        if(!match.matches()) throw new IllegalArgumentException("无法识别指令。示例：生成本月按日报表，或2026-08-01至2026-08-31按周报表。暂不支持区域过滤和预测");
        LocalDate today=LocalDate.now(clock), start, end;
        Granularity grain=Granularity.DAY;
        String period=match.group(1);
        switch(period) {
            case "今天" -> { start=today; end=today; }
            case "昨天", "昨日" -> { start=today.minusDays(1); end=start; }
            case "本周", "上周" -> { start=today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)); if(period.equals("上周")) start=start.minusWeeks(1); end=start.plusDays(6); }
            case "本月", "上月" -> { start=today.withDayOfMonth(1); if(period.equals("上月")) start=start.minusMonths(1); end=start.with(TemporalAdjusters.lastDayOfMonth()); }
            case "本年", "今年", "去年" -> { start=today.withDayOfYear(1); if(period.equals("去年")) start=start.minusYears(1); end=start.with(TemporalAdjusters.lastDayOfYear()); grain=Granularity.MONTH; }
            default -> {
                String[] dates=period.split("至|到|~");
                try { start=LocalDate.parse(dates[0]); end=LocalDate.parse(dates[1]); }
                catch(DateTimeException e) { throw new IllegalArgumentException("日期无效，请使用真实的 yyyy-MM-dd 日期"); }
            }
        }
        if(match.group(2)!=null) grain=switch(match.group(2)) { case "日" -> Granularity.DAY; case "周" -> Granularity.WEEK; case "月" -> Granularity.MONTH; default -> Granularity.YEAR; };
        return new ReportRange(start,end,grain);
    }

    public List<Bucket> buckets() {
        List<Bucket> result=new ArrayList<>();
        LocalDate cursor=startDate;
        while(!cursor.isAfter(endDate)) {
            LocalDate last=switch(granularity) {
                case DAY -> cursor;
                case WEEK -> cursor.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
                case MONTH -> cursor.with(TemporalAdjusters.lastDayOfMonth());
                case YEAR -> cursor.with(TemporalAdjusters.lastDayOfYear());
            };
            if(last.isAfter(endDate)) last=endDate;
            result.add(new Bucket(cursor,last)); cursor=last.plusDays(1);
        }
        return List.copyOf(result);
    }
}
