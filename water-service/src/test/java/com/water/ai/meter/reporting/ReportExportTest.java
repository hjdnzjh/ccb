package com.water.ai.meter.reporting;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.zip.*;
import static org.assertj.core.api.Assertions.*;

class ReportExportTest {
    @TempDir Path temporary;
    static ReportSnapshot snapshot(int days) {
        var range=new ReportRange(LocalDate.of(2026,1,1),LocalDate.of(2026,1,1).plusDays(days-1),ReportRange.Granularity.DAY);
        var metrics=new ReportSnapshot.Metrics(new BigDecimal("12.34"),new BigDecimal("45.67"),1,2,new BigDecimal("50.00"),2,1);
        return new ReportSnapshot("00000000-0000-0000-0000-000000000001",LocalDateTime.of(2026,9,18,12,0),"Asia/Shanghai",range,metrics,
                range.buckets().stream().map(b->new ReportSnapshot.Row(b.startDate(),b.endDate(),metrics)).toList(),List.of("中文统计口径", "=HYPERLINK(\"https://example.invalid\")"));
    }
    @Test void xlsxIsOpenXmlWithTypedNumericCellsAndNoFormulaExecution() throws Exception {
        byte[] bytes=new ReportExporter("").xlsx(snapshot(2));
        Path target=Path.of("target/report-export-test"); Files.createDirectories(target);
        Files.write(target.resolve("report.xlsx"),bytes);
        Map<String,String> entries=new HashMap<>();
        try(var zip=new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while((entry=zip.getNextEntry())!=null) entries.put(entry.getName(),new String(zip.readAllBytes(),StandardCharsets.UTF_8));
        }
        assertThat(entries).containsKeys("[Content_Types].xml","xl/workbook.xml","xl/worksheets/sheet1.xml","xl/worksheets/sheet2.xml");
        assertThat(entries.get("xl/worksheets/sheet1.xml")).contains("12.34","45.67","立方米").doesNotContain("<f>");
        assertThat(entries.get("xl/worksheets/sheet2.xml")).contains("inlineStr","=HYPERLINK").doesNotContain("<f>");
        var factory=javax.xml.parsers.DocumentBuilderFactory.newInstance();
        for(String xml:entries.values()) factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }
    @Test void pdfHasExtractableChineseAndPagesWithoutLosingFinalRow() throws Exception {
        String configured=System.getenv("REPORT_PDF_FONT");
        String path=configured==null ? "C:/Windows/Fonts/simhei.ttf" : configured;
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.isRegularFile(Path.of(path)),"Set REPORT_PDF_FONT to a Chinese TTF to test embedded PDF fonts");
        byte[] bytes=new ReportExporter(path).pdf(snapshot(90));
        try(var doc=Loader.loadPDF(bytes)) {
            assertThat(doc.getNumberOfPages()).isGreaterThan(2);
            String text=new PDFTextStripper().getText(doc);
            assertThat(text).contains("水务统计报表","2026-03-31","中文统计口径","45.67");
            Path target=Path.of("target/report-export-test"); Files.createDirectories(target);
            Files.write(target.resolve("report.pdf"),bytes);
            javax.imageio.ImageIO.write(new org.apache.pdfbox.rendering.PDFRenderer(doc).renderImageWithDPI(0,110),"png",target.resolve("page-1.png").toFile());
            javax.imageio.ImageIO.write(new org.apache.pdfbox.rendering.PDFRenderer(doc).renderImageWithDPI(doc.getNumberOfPages()-1,110),"png",target.resolve("page-last.png").toFile());
        }
    }
    @Test void badConfiguredFontDoesNotFallBackToUnreadablePdf() {
        assertThatThrownBy(()->new ReportExporter(temporary.resolve("missing.ttf").toString()).pdf(snapshot(1)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("中文字体");
    }
}
