package com.water.ai.meter.security;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpStatusCodeException;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class AgentGatewayController {
    private final RestTemplate rest;
    @Value("${agent-engine.url:http://localhost:8087}") private String engine;
    @RequestMapping(value="/agent-api/**",method={RequestMethod.GET,RequestMethod.POST})
    public ResponseEntity<?> proxy(HttpServletRequest request,@RequestBody(required=false) String body) {
        String path=request.getServletPath().substring("/agent-api".length());
        // Only known engine routes; never accept a client-provided destination or traversal.
        if(!path.matches("/(health|status|agents|agents/[A-Za-z0-9_-]+/execute|meter-reading|billing|anomaly/detect|analysis/(report|forecast)|collection/execute|workflow/create)"))
            return ResponseEntity.status(404).body(Map.of("success",false,"message","智能体接口不存在"));
        HttpHeaders headers=new HttpHeaders();headers.setContentType(MediaType.APPLICATION_JSON);
        try {
            var result=rest.exchange(engine+"/api"+path,HttpMethod.valueOf(request.getMethod()),new HttpEntity<>(body,headers),byte[].class);
            return ResponseEntity.status(result.getStatusCode()).contentType(MediaType.APPLICATION_JSON).body(result.getBody());
        } catch(HttpStatusCodeException e) {
            return ResponseEntity.status(e.getStatusCode()).contentType(MediaType.APPLICATION_JSON).body(e.getResponseBodyAsByteArray());
        }
    }
}
