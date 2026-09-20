package com.water.ai.meter.automation;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Competition protocol, local Asia/Shanghai timestamp, m³/h, m³, Celsius. */
public record DeviceReport(String meterNo, LocalDateTime timestamp, BigDecimal flow,
                           BigDecimal total, BigDecimal temperature, String valve, String alarm) {
    public record Alert(String type, String severity, String description) {}
    public static DeviceReport parse(String packet, LocalDateTime now) {
        if (packet == null || packet.length() > 500) throw new IllegalArgumentException("报文为空或过长");
        String[] f = packet.split("\\|", -1);
        if (f.length != 7) throw new IllegalArgumentException("报文必须有七字段：表号|ISO时间|瞬时流量|累计流量|水温|OPEN/CLOSED|报警码");
        try {
            if (!f[0].matches("[A-Za-z0-9_-]{1,50}")) throw new IllegalArgumentException();
            LocalDateTime time = LocalDateTime.parse(f[1]);
            if (time.isAfter(now.plusMinutes(5)) || time.getNano() != 0 || time.isBefore(now.minusDays(30)))
                throw new IllegalArgumentException();
            BigDecimal flow = decimal(f[2],3), total = decimal(f[3],2), temp = decimal(f[4],2);
            if (flow.signum()<0 || total.signum()<0 || total.compareTo(new BigDecimal("9999999999.99"))>0
                    || flow.compareTo(new BigDecimal("1000000"))>0 || temp.compareTo(new BigDecimal("-50"))<0
                    || temp.compareTo(new BigDecimal("150"))>0 || !Set.of("OPEN","CLOSED").contains(f[5])
                    || !f[6].matches("[A-Za-z0-9_-]{1,20}")) throw new IllegalArgumentException();
            return new DeviceReport(f[0],time,flow,total,temp,f[5],f[6]);
        } catch (RuntimeException e) { throw new IllegalArgumentException("设备报文字段无效（时间须在过去30天至未来5分钟，累计读数最多两位小数）"); }
    }
    private static BigDecimal decimal(String text, int scale) {
        if (!text.matches("-?\\d{1,12}(\\.\\d{1,"+scale+"})?")) throw new IllegalArgumentException();
        return new BigDecimal(text);
    }
    public List<Alert> anomalies() {
        List<Alert> alerts = new ArrayList<>();
        if (!"0".equals(alarm)) alerts.add(new Alert("meter_fault","high","设备报警码："+alarm));
        if (flow.compareTo(BigDecimal.TEN)>0) alerts.add(new Alert("high_flow","high","瞬时流量超过10 m³/h"));
        if ("CLOSED".equals(valve) && flow.signum()>0) alerts.add(new Alert("valve_fault","critical","阀门关闭时仍检测到流量"));
        if (temperature.compareTo(BigDecimal.ZERO)<0 || temperature.compareTo(new BigDecimal("60"))>0)
            alerts.add(new Alert("temperature_abnormal","medium","供水水温不在0至60℃范围内"));
        return List.copyOf(alerts);
    }
}
