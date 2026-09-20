package com.water.ai.meter.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * RestTemplate配置
 * 用于调用智能体引擎API
 */
@Configuration
public class RestTemplateConfig {

    @Value("${agent-engine.timeout:30000}")
    private int timeout;
    @Value("${agent-engine.internal-token:}")
    private String internalToken;
    @Value("${agent-engine.url:http://localhost:8087}")
    private String agentUrl;

    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        RestTemplate template=new RestTemplate(factory);
        java.net.URI allowed=java.net.URI.create(agentUrl);
        template.getInterceptors().add((request,body,execution)->{
            java.net.URI target=request.getURI();
            if(java.util.Objects.equals(allowed.getScheme(),target.getScheme()) && java.util.Objects.equals(allowed.getHost(),target.getHost()) && allowed.getPort()==target.getPort())
                request.getHeaders().set("X-Agent-Token",internalToken);
            return execution.execute(request,body);
        });
        return template;
    }
}
