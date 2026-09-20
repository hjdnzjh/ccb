package com.water.ai.meter.diagnosis;
import java.time.*;
import org.springframework.context.annotation.*;
@Configuration
public class DiagnosisClockConfig {
    @Bean public Clock diagnosisClock(){return Clock.system(ZoneId.of("Asia/Shanghai"));}
}
