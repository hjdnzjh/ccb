package com.water.ai.meter.reporting;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;

public record ReportSnapshot(String id, LocalDateTime generatedAt, String timezone, ReportRange range,
                             Metrics totals, List<Row> rows, List<String> definitions) {
    // Store decimal values as strings: MySQL JSON numeric normalization must not round money or erase scale.
    public record Metrics(@JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal waterUsage,
                          @JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal revenue, long faultMeters, long meterBase,
                          @JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal faultRate, long readingCount, long paymentCount) {}
    public record Row(LocalDate startDate, LocalDate endDate, Metrics metrics) {}
}
