package com.water.ai.meter.reporting;

import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

@Component
public class ReportExporter {
    private static final String[] HEADERS={"开始日期","结束日期","用水量(立方米)","收入(元)","故障水表数","设备基数","故障率(%)","确认抄表数","收款笔数"};
    private final String configuredFont;
    public ReportExporter(@Value("${reporting.pdf-font:${REPORT_PDF_FONT:}}") String configuredFont) { this.configuredFont=configuredFont; }

    /** OOXML cells are inline strings or numbers only; no formulas, macros or user-supplied ZIP paths. */
    public byte[] xlsx(ReportSnapshot report) throws IOException {
        ByteArrayOutputStream output=new ByteArrayOutputStream();
        try(ZipOutputStream zip=new ZipOutputStream(output,StandardCharsets.UTF_8)) {
            entry(zip,"[Content_Types].xml","<?xml version=\"1.0\" encoding=\"UTF-8\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/><Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/><Override PartName=\"/xl/worksheets/sheet2.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/><Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/></Types>");
            entry(zip,"_rels/.rels","<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>");
            entry(zip,"xl/workbook.xml","<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets><sheet name=\"统计数据\" sheetId=\"1\" r:id=\"rId1\"/><sheet name=\"快照与口径\" sheetId=\"2\" r:id=\"rId2\"/></sheets></workbook>");
            entry(zip,"xl/_rels/workbook.xml.rels","<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/><Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet2.xml\"/><Relationship Id=\"rId3\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/></Relationships>");
            entry(zip,"xl/styles.xml","<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><fonts count=\"2\"><font><sz val=\"11\"/><name val=\"Microsoft YaHei\"/></font><font><b/><sz val=\"11\"/><name val=\"Microsoft YaHei\"/></font></fonts><fills count=\"2\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill></fills><borders count=\"1\"><border/></borders><cellStyleXfs count=\"1\"><xf/></cellStyleXfs><cellXfs count=\"3\"><xf fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/><xf fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/><xf fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyAlignment=\"1\"><alignment wrapText=\"1\" vertical=\"top\"/></xf></cellXfs><cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles></styleSheet>");
            StringBuilder data=new StringBuilder(sheetStart("<cols><col min=\"1\" max=\"9\" width=\"19\" customWidth=\"1\"/></cols>"));
            appendRow(data,1,Arrays.asList(HEADERS),1);
            List<Object> total=values("范围总计","",report.totals()); appendRow(data,2,total,1);
            int row=3;
            for(var item:report.rows()) appendRow(data,row++,values(item.startDate().toString(),item.endDate().toString(),item.metrics()),0);
            entry(zip,"xl/worksheets/sheet1.xml",data.append("</sheetData></worksheet>").toString());
            StringBuilder notes=new StringBuilder(sheetStart("<cols><col min=\"1\" max=\"1\" width=\"130\" customWidth=\"1\"/></cols>"));
            List<String> lines=new ArrayList<>(List.of("水务统计报表", "快照编号："+report.id(),"生成时间："+report.generatedAt()+" "+report.timezone(),
                    "范围："+report.range().startDate()+" 至 "+report.range().endDate()+"；粒度："+report.range().granularity()));
            lines.addAll(report.definitions()); row=1;
            for(String line:lines) appendRow(notes,row++,List.of(line),2);
            entry(zip,"xl/worksheets/sheet2.xml",notes.append("</sheetData></worksheet>").toString());
        }
        return output.toByteArray();
    }
    private static String sheetStart(String columns) { return "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"+columns+"<sheetData>"; }
    private static void entry(ZipOutputStream zip,String name,String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name)); zip.write(content.getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
    }
    private static List<Object> values(String start,String end,ReportSnapshot.Metrics m) {
        return List.of(start,end,m.waterUsage(),m.revenue(),m.faultMeters(),m.meterBase(),m.faultRate()==null ? "不适用" : m.faultRate(),m.readingCount(),m.paymentCount());
    }
    private static void appendRow(StringBuilder xml,int row,List<?> values,int style) {
        xml.append("<row r=\"").append(row).append("\"");
        if(style==2) xml.append(" ht=\"56\" customHeight=\"1\"");
        xml.append('>');
        for(int i=0;i<values.size();i++) {
            Object value=values.get(i);
            xml.append("<c r=\"").append((char)('A'+i)).append(row).append("\" s=\"").append(style).append("\"");
            if(value instanceof Number) xml.append("><v>").append(value).append("</v></c>");
            else xml.append(" t=\"inlineStr\"><is><t xml:space=\"preserve\">").append(escape(value.toString())).append("</t></is></c>");
        }
        xml.append("</row>");
    }
    private static String escape(String text) { return text.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&apos;"); }

    public byte[] pdf(ReportSnapshot report) throws IOException {
        Path path=fontPath();
        try(PDDocument document=new PDDocument(); ByteArrayOutputStream output=new ByteArrayOutputStream()) {
            // PDFBox embeds only used glyphs. Font path is server configuration, never a request parameter.
            PDType0Font font=PDType0Font.load(document,path.toFile());
            try(Pages pages=new Pages(document,font,report.id())) {
                pages.line("水务统计报表",20);
                pages.line(report.range().startDate()+" 至 "+report.range().endDate()+" / "+report.range().granularity()+" / "+report.timezone(),11);
                pages.line("生成时间："+report.generatedAt(),10);
                pages.line("总用水量 "+report.totals().waterUsage()+" 立方米    收款收入 "+report.totals().revenue()+" 元",12);
                pages.line("故障水表 "+report.totals().faultMeters()+" / 设备基数 "+report.totals().meterBase()+"    故障率 "+rate(report.totals())+"    确认抄表 "+report.totals().readingCount()+"    收款笔数 "+report.totals().paymentCount(),10);
                for(String definition:report.definitions()) pages.wrapped(definition,9);
                pages.line("分期统计",12);
                pages.tableHeader();
                for(var row:report.rows()) pages.tableRow(values(row.startDate().toString(),row.endDate().toString(),row.metrics()));
            }
            document.save(output); return output.toByteArray();
        }
    }
    private static String rate(ReportSnapshot.Metrics metrics) { return metrics.faultRate()==null ? "不适用" : metrics.faultRate()+"%"; }
    private Path fontPath() {
        if(configuredFont!=null && !configuredFont.isBlank()) {
            Path path=Path.of(configuredFont);
            if(!Files.isRegularFile(path)) throw new IllegalStateException("中文字体不可用，请配置 REPORT_PDF_FONT 为服务器上的中文TTF文件");
            return path;
        }
        for(String candidate:List.of("C:/Windows/Fonts/simhei.ttf","/usr/share/fonts/truetype/wqy/wqy-zenhei.ttf","/usr/share/fonts/truetype/arphic/uming.ttf"))
            if(Files.isRegularFile(Path.of(candidate))) return Path.of(candidate);
        throw new IllegalStateException("缺少中文字体，请配置 REPORT_PDF_FONT 为服务器上的中文TTF文件");
    }

    private static class Pages implements AutoCloseable {
        private final PDDocument document;
        private final PDType0Font font;
        private final String id;
        private PDPageContentStream stream;
        private float y;
        private static final float MARGIN=36, WIDTH=770;
        private static final float[] COLS={82,82,105,100,72,72,72,85,100};
        Pages(PDDocument document,PDType0Font font,String id) throws IOException { this.document=document; this.font=font; this.id=id; next(); }
        void next() throws IOException {
            if(stream!=null) stream.close();
            var page=new PDPage(new PDRectangle(PDRectangle.A4.getHeight(),PDRectangle.A4.getWidth())); document.addPage(page);
            stream=new PDPageContentStream(document,page); y=page.getMediaBox().getHeight()-MARGIN;
            text("快照 "+id+"     第 "+document.getNumberOfPages()+" 页",MARGIN,20,8);
        }
        void text(String value,float x,float baseline,float size) throws IOException {
            stream.beginText(); stream.setFont(font,size); stream.newLineAtOffset(x,baseline); stream.showText(value); stream.endText();
        }
        void line(String value,float size) throws IOException { if(y<size+45) next(); text(value,MARGIN,y,size); y-=size+8; }
        void wrapped(String value,float size) throws IOException {
            StringBuilder line=new StringBuilder();
            for(int cp:value.codePoints().toArray()) {
                String candidate=line.toString()+new String(Character.toChars(cp));
                if(font.getStringWidth(candidate)/1000*size>WIDTH) { line(line.toString(),size); line.setLength(0); }
                line.appendCodePoint(cp);
            }
            if(!line.isEmpty()) line(line.toString(),size);
        }
        void tableHeader() throws IOException { if(y<75) next(); draw(Arrays.asList(HEADERS),true); }
        void tableRow(List<?> values) throws IOException { if(y<60) { next(); tableHeader(); } draw(values,false); }
        void draw(List<?> cells,boolean header) throws IOException {
            if(header) { stream.setNonStrokingColor(0.91f,0.95f,0.98f); stream.addRect(MARGIN,y-5,WIDTH,20); stream.fill(); stream.setNonStrokingColor(0f,0f,0f); }
            float x=MARGIN+3;
            for(int i=0;i<cells.size();i++) {
                String value=cells.get(i).toString();
                float size=Math.min(9, (COLS[i]-8)*1000/Math.max(1,font.getStringWidth(value)));
                text(value,x,y,size); x+=COLS[i];
            }
            y-=22;
        }
        @Override public void close() throws IOException { if(stream!=null) stream.close(); }
    }
}
