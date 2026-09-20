package com.water.ai.meter.diagnosis;
import com.water.ai.meter.common.ApiResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.*;
import org.springframework.web.server.ResponseStatusException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
@RestController @RequestMapping("/api/v1/lab")
public class LabController {
 private final RestTemplate http;private final String token,url;
 public LabController(@Value("${agent-engine.internal-token:}")String token,@Value("${innovation.lab-url:http://127.0.0.1:8088}")String url){this.token=token;this.url=url;var f=new SimpleClientHttpRequestFactory();f.setConnectTimeout(1000);f.setReadTimeout(3000);http=new RestTemplate(f);}
 @RequestMapping(value={"/replays","/replays/{id}","/replays/{id}/control","/replays/{id}/report"},method={RequestMethod.GET,RequestMethod.POST}) public ApiResult<?> proxy(HttpServletRequest request,@RequestBody(required=false)Map<String,Object> body){
  String path=request.getServletPath().substring("/api/v1/lab".length());if(!path.matches("/replays(/[a-f0-9]{32}(/(control|report))?)?"))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"演练接口不存在");
  var headers=new HttpHeaders();headers.setContentType(MediaType.APPLICATION_JSON);headers.set("X-Agent-Token",token);
  try{return ApiResult.ok(http.exchange(url+"/api/lab"+path,HttpMethod.valueOf(request.getMethod()),new HttpEntity<>(body,headers),Map.class).getBody());}
  catch(HttpStatusCodeException e){throw new ResponseStatusException(e.getStatusCode(),Objects.toString(DiagnosisSupport.map(e.getResponseBodyAsString()).get("message"),"演练请求失败"));}
  catch(ResourceAccessException e){throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"独立演练服务未启动，请运行 tools/start-innovation-lab.ps1");}
 }
}
