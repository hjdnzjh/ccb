package com.water.ai.meter.automation;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import static org.assertj.core.api.Assertions.*;

class AutomationRulesTest {
    final LocalDateTime now = LocalDateTime.of(2026,9,18,12,0);
    @Test void sevenFieldsPreserveDecimalAndDetectClosedValveFlow() {
        var report = DeviceReport.parse("WM-1|2026-09-18T11:59:00|0.20|123.45|25.0|CLOSED|0", now);
        assertThat(report.total()).isEqualByComparingTo("123.45");
        assertThat(report.anomalies()).extracting(DeviceReport.Alert::severity).containsExactly("critical");
    }
    @Test void rejectMissingFieldsFutureTimeAndExcessPrecision() {
        assertThatThrownBy(() -> DeviceReport.parse("WM-1|2026-09-18",now)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DeviceReport.parse("WM-1|2026-09-19T00:00:00|0|123.45|25|OPEN|0",now)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DeviceReport.parse("WM-1|2026-09-18T11:59:00|0|123.456|25|OPEN|0",now)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void deterministicAlarmAndHighFlowClassification() {
        var report = DeviceReport.parse("WM-1|2026-09-18T11:59:00|11|124|25|OPEN|E01",now);
        assertThat(report.anomalies()).extracting(DeviceReport.Alert::severity).containsExactly("high","high");
    }
    @Test void penaltyUsesOutstandingPrincipalAndRoundsOnlyAtDayBoundary() {
        assertThat(PenaltyService.dailyAmount(b("100"),b("40"),b("0"),b("0.001"),b("0.1"))).isEqualByComparingTo("0.06");
        assertThat(PenaltyService.dailyAmount(b("100"),b("0"),b("9.98"),b("0.001"),b("0.1"))).isEqualByComparingTo("0.02");
        assertThat(PenaltyService.dailyAmount(b("100"),b("100"),b("0"),b("0.001"),b("0.1"))).isEqualByComparingTo("0.00");
        assertThat(PenaltyService.dailyAmount(b("5"),b("0"),b("0"),b("0.001"),b("0.1"))).isEqualByComparingTo("0.01");
    }
    private BigDecimal b(String value) { return new BigDecimal(value); }
}
