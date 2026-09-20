package com.water.ai.meter.reporting;

import org.junit.jupiter.api.Test;
import java.time.*;
import static org.assertj.core.api.Assertions.*;

class ReportRangeTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-18T00:00:00Z"), ZoneId.of("Asia/Shanghai"));
    @Test void parsesRelativeRangesAndExplicitGranularity() {
        assertThat(ReportRange.parse("生成上月按周综合报表", clock))
                .isEqualTo(new ReportRange(LocalDate.of(2026,8,1),LocalDate.of(2026,8,31),ReportRange.Granularity.WEEK));
        assertThat(ReportRange.parse("本周报表", clock).startDate()).isEqualTo(LocalDate.of(2026,9,14));
        assertThat(ReportRange.parse("本年报表", clock).granularity()).isEqualTo(ReportRange.Granularity.MONTH);
    }
    @Test void parsesLeapYearAndClipsFirstAndLastBuckets() {
        var range=ReportRange.parse("2024-02-28至2024-03-02按月用水量报表",clock);
        assertThat(range.buckets()).containsExactly(
                new ReportRange.Bucket(LocalDate.of(2024,2,28),LocalDate.of(2024,2,29)),
                new ReportRange.Bucket(LocalDate.of(2024,3,1),LocalDate.of(2024,3,2)));
        assertThat(new ReportRange(LocalDate.of(2026,9,13),LocalDate.of(2026,9,15),ReportRange.Granularity.WEEK).buckets()).hasSize(2);
    }
    @Test void rejectsAmbiguityUnknownFiltersAndUnsafeOrExcessiveRange() {
        for(String input:new String[]{"预测下月收入","本月上月报表","西湖区本月报表","2026-02-30至2026-03-02按日报表","2026-09-20至2026-09-01按日报表","本月按周按日报表","本月; DROP TABLE bill"}) {
            assertThatThrownBy(() -> ReportRange.parse(input,clock)).as(input).isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> new ReportRange(LocalDate.of(2020,1,1),LocalDate.of(2026,1,1),ReportRange.Granularity.DAY)).isInstanceOf(IllegalArgumentException.class);
    }
}
