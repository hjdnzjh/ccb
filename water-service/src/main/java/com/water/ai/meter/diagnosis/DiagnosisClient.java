package com.water.ai.meter.diagnosis;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import java.util.*;
import static com.water.ai.meter.diagnosis.DiagnosisSupport.*;

@Component
public class DiagnosisClient {
 private final RestTemplate http;private final String url,token;
 public DiagnosisClient(@Value("${agent-engine.url:http://localhost:8087}")String url,@Value("${agent-engine.internal-token:}")String token){this.url=url;this.token=token;var f=new SimpleClientHttpRequestFactory();f.setConnectTimeout(2000);f.setReadTimeout(2000);http=new RestTemplate(f);}
 @SuppressWarnings("unchecked") public Map<String,Object> score(long meter,List<Map<String,Object>> history){
  List<Map<String,Object>> input=new ArrayList<>();for(var row:history){var r=new LinkedHashMap<>(row);r.put("reportedAt",time(row.get("reportedAt")).atOffset(java.time.ZoneOffset.ofHours(8)).toString());r.put("valve","OPEN".equals(row.get("valve"))?1:0);r.put("alarm","0".equals(row.get("alarm"))?0:1);input.add(r);}
  var h=new HttpHeaders();h.setContentType(MediaType.APPLICATION_JSON);h.set("X-Agent-Token",token);
  var result=http.postForObject(url+"/api/internal/diagnosis/score",new HttpEntity<>(Map.of("meterId",meter,"observations",input),h),Map.class);
  if(result==null||!Set.of("normal","needs_review","insufficient_data").contains(result.get("decision"))||!(result.get("baseline") instanceof Map)||!(result.get("features") instanceof Map)||!(result.get("reasonCodes") instanceof List))throw new IllegalStateException("诊断引擎响应无效");
  return result;
 }
 public Map<String,Object> fallback(Map<String,Object> o){
  List<String> reasons=new ArrayList<>();double flow=((Number)o.get("flow")).doubleValue(),temp=((Number)o.get("temperature")).doubleValue();
  if(!"0".equals(o.get("alarm")))reasons.add("device_alarm");if(flow>10)reasons.add("high_flow");if("CLOSED".equals(o.get("valve"))&&flow>0)reasons.add("flow_while_valve_closed");if(temp<0||temp>60)reasons.add("temperature_abnormal");
  return Map.of("decision",reasons.isEmpty()?"insufficient_data":"needs_review","reasonCodes",reasons,"baseline",Map.of("ready",false),"features",Map.of("instantaneousFlow",flow,"fallbackReason","engine_unavailable"),"modelVersion","rules-only","fallbackReason","engine_unavailable");
 }
}
