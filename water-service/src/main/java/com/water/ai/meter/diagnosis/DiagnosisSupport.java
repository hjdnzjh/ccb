package com.water.ai.meter.diagnosis;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

final class DiagnosisSupport {
    private static final ObjectMapper JSON=new ObjectMapper().findAndRegisterModules();
    static String json(Object value){try{return JSON.writeValueAsString(value);}catch(Exception e){throw new IllegalArgumentException("诊断数据无法序列化",e);}}
    static Map<String,Object> map(Object value){try{return value instanceof Map?JSON.convertValue(value,new TypeReference<>(){}):JSON.readValue(Objects.toString(value,"{}"),new TypeReference<>(){});}catch(Exception e){return Map.of();}}
    static List<Long> ids(Object value){try{return JSON.readValue(Objects.toString(value,"[]"),new TypeReference<>(){});}catch(Exception e){throw new IllegalArgumentException("诊断范围配置无效");}}
    static long number(Map<String,Object> row,String key){return row.get(key)==null?0:((Number)row.get(key)).longValue();}
    static LocalDateTime time(Object value){return value instanceof Timestamp t?t.toLocalDateTime():value instanceof LocalDateTime t?t:LocalDateTime.parse(value.toString());}
    static String hash(String s){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    static void key(String key){if(key==null||!key.matches("[A-Za-z0-9_:.-]{1,100}"))throw new IllegalArgumentException("请求号不能为空且最多100个英文、数字或连接符");}
    static String note(String note){if(note==null||note.isBlank()||note.length()>1000)throw new IllegalArgumentException("请填写1至1000字说明");return note.trim();}
    private DiagnosisSupport(){}
}
