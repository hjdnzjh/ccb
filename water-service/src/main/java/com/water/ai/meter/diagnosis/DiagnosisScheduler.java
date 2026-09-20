package com.water.ai.meter.diagnosis;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
@Component @RequiredArgsConstructor
@ConditionalOnProperty(name="diagnosis.scheduling.enabled",havingValue="true",matchIfMissing=true)
public class DiagnosisScheduler {
 private final DiagnosisService diagnosis;private final DiagnosticProbeService probes;private final WorkOrderVerificationService verification;
 @Scheduled(fixedDelay=10000,initialDelay=30000) public void tick(){try{diagnosis.tick();probes.tick();verification.tick();}catch(RuntimeException e){org.slf4j.LoggerFactory.getLogger(getClass()).warn("诊断调度未完成，持久任务将在下轮重试",e);}}
}
