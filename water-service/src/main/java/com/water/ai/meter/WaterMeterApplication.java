package com.water.ai.meter;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 基于AI的水表抄表收费管理系统 - 主启动类
 * 
 * @author Water AI Team
 * @version 1.0.0
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
@EnableScheduling
@MapperScan("com.water.ai.meter.mapper")
public class WaterMeterApplication {

    public static void main(String[] args) {
        // #region agent log
        try {
            java.util.function.Function<Integer, Boolean> probe = port -> {
                try (java.net.Socket s = new java.net.Socket()) {
                    s.connect(new java.net.InetSocketAddress("127.0.0.1", port), 800);
                    return true;
                } catch (Exception e) {
                    return false;
                }
            };
            boolean redis6379 = probe.apply(6379);
            boolean mysql3308 = probe.apply(3308);
            boolean dockerPipe = new java.io.File("\\\\.\\pipe\\docker_engine").exists();
            String redisHost = System.getenv().getOrDefault("SPRING_DATA_REDIS_HOST", "unset");
            String redisPort = System.getenv().getOrDefault("SPRING_DATA_REDIS_PORT", "unset");
            String line = "{\"sessionId\":\"aa5fe2\",\"runId\":\"post-fix\",\"hypothesisId\":\"A\",\"location\":\"WaterMeterApplication.java:main\",\"message\":\"tcp probes before Spring start\",\"data\":{\"redis6379\":" + redis6379 + ",\"mysql3308\":" + mysql3308 + ",\"dockerEngineHint\":\"" + (dockerPipe ? "pipe-present" : "pipe-missing") + "\",\"envRedisHost\":\"" + redisHost + "\",\"envRedisPort\":\"" + redisPort + "\"},\"timestamp\":" + System.currentTimeMillis() + "}\n";
            java.nio.file.Files.writeString(java.nio.file.Path.of("D:\\ccb\\debug-aa5fe2.log"), line, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (Exception ignored) {}
        // #endregion
        try {
            SpringApplication.run(WaterMeterApplication.class, args);
            // #region agent log
            try {
                String ok = "{\"sessionId\":\"aa5fe2\",\"runId\":\"post-fix\",\"hypothesisId\":\"E\",\"location\":\"WaterMeterApplication.java:main:post\",\"message\":\"SpringApplication.run returned\",\"data\":{\"started\":true},\"timestamp\":" + System.currentTimeMillis() + "}\n";
                java.nio.file.Files.writeString(java.nio.file.Path.of("D:\\ccb\\debug-aa5fe2.log"), ok, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
            } catch (Exception ignored) {}
            // #endregion
        } catch (Exception ex) {
            // #region agent log
            try {
                Throwable c = ex;
                while (c.getCause() != null) c = c.getCause();
                String fail = "{\"sessionId\":\"aa5fe2\",\"runId\":\"post-fix\",\"hypothesisId\":\"E\",\"location\":\"WaterMeterApplication.java:main:catch\",\"message\":\"SpringApplication.run failed\",\"data\":{\"exType\":\"" + ex.getClass().getSimpleName() + "\",\"rootType\":\"" + c.getClass().getSimpleName() + "\",\"rootMsg\":\"" + String.valueOf(c.getMessage()).replace("\"", "'") + "\"},\"timestamp\":" + System.currentTimeMillis() + "}\n";
                java.nio.file.Files.writeString(java.nio.file.Path.of("D:\\ccb\\debug-aa5fe2.log"), fail, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
            } catch (Exception ignored) {}
            // #endregion
            throw ex;
        }
        System.out.println("========================================");
        System.out.println("   水表抄表收费管理系统启动成功!        ");
        System.out.println("   API文档: http://localhost:8080/doc   ");
        System.out.println("========================================");
    }
}