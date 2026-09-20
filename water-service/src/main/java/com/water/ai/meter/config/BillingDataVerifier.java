package com.water.ai.meter.config;

import com.water.ai.meter.mapper.BillMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Refuse to enable new billing on ambiguous historical reading/bill relationships. */
@Component
@RequiredArgsConstructor
public class BillingDataVerifier implements ApplicationRunner {
    private final BillMapper billMapper;

    @Override
    public void run(ApplicationArguments args) {
        var duplicates = billMapper.selectDuplicateReadingIds();
        if (!duplicates.isEmpty()) {
            throw new IllegalStateException("存在重复有效账单，请人工核对抄表 ID：" + duplicates);
        }
    }
}
