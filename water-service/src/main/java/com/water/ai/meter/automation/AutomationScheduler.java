package com.water.ai.meter.automation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name="automation.scheduling.enabled",havingValue="true",matchIfMissing=true)
public class AutomationScheduler {
    private final CollectionService collection;
    private final PenaltyService penalty;
    private final AutonomousCoverageService coverage;
    @Scheduled(fixedDelayString="${automation.collection-delay-ms:10000}",initialDelay=20000)
    public void collect() {
        try { coverage.reconcile(); collection.tick(); } catch(RuntimeException e) { log.error("采集调度失败；任务保留等待下次运行",e); }
    }
    @Scheduled(fixedDelayString="${automation.penalty-delay-ms:3600000}",initialDelay=30000)
    public void accrue() {
        try { penalty.accrue(); } catch(RuntimeException e) { log.error("违约金计费失败；已登记日期不会重复计费",e); }
    }
}
