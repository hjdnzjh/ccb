package com.water.ai.meter.tariff;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AnnualTariffCalculatorTest {
    @Test void crossingFirstTierUsesOnlyRemainingAllowance() {
        var q=AnnualTariffCalculator.calculate("HZ_RES_V1",new BigDecimal("210"),new BigDecimal("20"));
        assertThat(q.water()).isEqualByComparingTo("51.30");
        assertThat(q.sewage()).isEqualByComparingTo("20.00");
        assertThat(q.total()).isEqualByComparingTo("71.30");
    }
    @Test void regionalSewageIsPerCubicMeter() {
        assertThat(AnnualTariffCalculator.calculate("YH_RES_V1",BigDecimal.ZERO,new BigDecimal("20")).total()).isEqualByComparingTo("57.00");
        assertThat(AnnualTariffCalculator.calculate("YH_WEST_RES_V1",BigDecimal.ZERO,new BigDecimal("20")).total()).isEqualByComparingTo("51.00");
    }
    @Test void thirdTierAndZeroAreExact() {
        assertThat(AnnualTariffCalculator.calculate("HZ_RES_V1",new BigDecimal("300"),BigDecimal.ONE).total()).isEqualByComparingTo("6.70");
        assertThat(AnnualTariffCalculator.calculate("HZ_RES_V1",new BigDecimal("300"),BigDecimal.ZERO).total()).isEqualByComparingTo("0");
    }
    @Test void repeatedFractionalReadingsHaveSameTotalAsSingleReading() {
        BigDecimal usage=BigDecimal.ZERO,total=BigDecimal.ZERO;
        for(int i=0;i<100;i++) {var q=AnnualTariffCalculator.calculate("YH_RES_V1",usage,new BigDecimal("0.01"));total=total.add(q.total());usage=usage.add(new BigDecimal("0.01"));}
        assertThat(total).isEqualByComparingTo(AnnualTariffCalculator.calculate("YH_RES_V1",BigDecimal.ZERO,BigDecimal.ONE).total());
    }
    @Test void invalidProfileAndUsageAreRejected() {
        assertThatIllegalArgumentException().isThrownBy(()->AnnualTariffCalculator.calculate("UNKNOWN",BigDecimal.ZERO,BigDecimal.ONE));
        assertThatIllegalArgumentException().isThrownBy(()->AnnualTariffCalculator.calculate("HZ_RES_V1",new BigDecimal("-1"),BigDecimal.ONE));
    }
}
