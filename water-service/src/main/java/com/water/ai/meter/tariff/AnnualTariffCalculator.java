package com.water.ai.meter.tariff;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/** Immutable, source-labelled residential presets. No floating-point money calculations. */
public final class AnnualTariffCalculator {
    private AnnualTariffCalculator() {}
    public record Profile(String key,String name,BigDecimal sewageUnit,String source) {}
    public record Quote(BigDecimal water,BigDecimal sewage,List<Map<String,Object>> tiers) {
        public BigDecimal total(){return water.add(sewage);}
    }
    private static final String YH_SOURCE="https://www.yuhang.gov.cn/art/2024/5/7/art_1229175009_4261409.html";
    public static final List<Profile> PROFILES=List.of(
        new Profile("HZ_RES_V1","杭州市区居民（资料预置V1）",new BigDecimal("1.00"),"https://drc.hangzhou.gov.cn/module/download/downfile.jsp?classid=0&filename=2fd79580eb9e4f6c9045ee05bac937f7.pdf"),
        new Profile("YH_RES_V1","余杭居民·其他地区（2024公示V1）",new BigDecimal("0.95"),YH_SOURCE),
        new Profile("YH_WEST_RES_V1","余杭居民·西部四镇（2024公示V1）",new BigDecimal("0.65"),YH_SOURCE));
    public static Profile profile(String key){return PROFILES.stream().filter(p->p.key().equals(key)).findFirst().orElseThrow(()->new IllegalArgumentException("请选择有效居民地区方案"));}
    public static BigDecimal money(BigDecimal v){return v.setScale(2,RoundingMode.HALF_UP);}
    public static void validUsage(BigDecimal v){if(v==null||v.signum()<0||v.scale()>3||v.compareTo(new BigDecimal("999999999.999"))>0)throw new IllegalArgumentException("用量须非负、最多三位小数且在允许范围内");}
    public static Quote calculate(String key,BigDecimal before,BigDecimal increment){
        var p=profile(key);validUsage(before);validUsage(increment);BigDecimal after=before.add(increment);validUsage(after);
        String[] lower={"0","216","300"},upper={"216","300","999999999.999"},prices={"1.90","2.85","5.70"};
        List<Map<String,Object>> tiers=new ArrayList<>();BigDecimal water=BigDecimal.ZERO.setScale(2);
        for(int i=0;i<3;i++){
            BigDecimal lo=new BigDecimal(lower[i]),width=new BigDecimal(upper[i]).subtract(lo),price=new BigDecimal(prices[i]);
            BigDecimal previous=before.subtract(lo).max(BigDecimal.ZERO).min(width),current=after.subtract(lo).max(BigDecimal.ZERO).min(width);
            BigDecimal quantity=current.subtract(previous),fee=money(current.multiply(price)).subtract(money(previous.multiply(price)));
            water=water.add(fee);
            if(quantity.signum()>0)tiers.add(Map.of("tier",i+1,"quantity",quantity,"unitPrice",price,"amount",fee));
        }
        BigDecimal sewage=money(after.multiply(p.sewageUnit())).subtract(money(before.multiply(p.sewageUnit())));
        return new Quote(water,sewage,List.copyOf(tiers));
    }
}
