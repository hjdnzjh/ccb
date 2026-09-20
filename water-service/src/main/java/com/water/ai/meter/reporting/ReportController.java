package com.water.ai.meter.reporting;

import com.water.ai.meter.common.ApiResult;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import java.time.*;
import java.util.Locale;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {
    private final ReportService service;
    private final ReportExporter exporter;
    public ReportController(ReportService service,ReportExporter exporter) { this.service=service; this.exporter=exporter; }
    public record Request(String command,LocalDate startDate,LocalDate endDate,String granularity) {}
    @PostMapping
    public ApiResult<ReportSnapshot> generate(@RequestBody Request input) {
        ReportRange range;
        if(input.command()!=null && !input.command().isBlank()) {
            if(input.startDate()!=null || input.endDate()!=null || input.granularity()!=null) throw new IllegalArgumentException("中文指令与结构化范围请二选一，避免歧义");
            range=ReportRange.parse(input.command(),Clock.system(ZoneId.of("Asia/Shanghai")));
        } else {
            ReportRange.Granularity grain;
            try { grain=ReportRange.Granularity.valueOf(input.granularity().toUpperCase(Locale.ROOT)); }
            catch(Exception e) { throw new IllegalArgumentException("粒度只能为 DAY、WEEK、MONTH、YEAR"); }
            range=new ReportRange(input.startDate(),input.endDate(),grain);
        }
        return ApiResult.ok(service.generate(range));
    }
    @GetMapping("/{id}")
    public ApiResult<ReportSnapshot> get(@PathVariable String id) { return ApiResult.ok(service.get(id)); }
    @GetMapping("/{id}/export")
    public ResponseEntity<byte[]> export(@PathVariable String id,@RequestParam String format) throws IOException {
        if(!"xlsx".equals(format) && !"pdf".equals(format)) throw new IllegalArgumentException("导出格式仅支持xlsx或pdf");
        var report=service.get(id);
        byte[] bytes="xlsx".equals(format) ? exporter.xlsx(report) : exporter.pdf(report);
        String type="xlsx".equals(format) ? "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" : "application/pdf";
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(type)).contentLength(bytes.length)
                .header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\"water-report-"+report.id()+"."+format+"\"")
                .header(HttpHeaders.CACHE_CONTROL,"no-store").body(bytes);
    }
    @ExceptionHandler(ReportService.ReportNotFoundException.class)
    public ResponseEntity<ApiResult<Object>> missing() { return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResult.fail("报表不存在")); }
}
