package com.water.ai.meter.assistant;

import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AssistantServiceTest {
    AssistantRepository repository;
    AssistantService service;
    @BeforeEach void setup() {
        repository=mock(AssistantRepository.class);
        service=new AssistantService(repository,Clock.fixed(Instant.parse("2026-03-31T04:00:00Z"),ZoneOffset.UTC));
        when(repository.areas()).thenReturn(List.of(
            Map.of("id",1L,"area_code","A001","area_name","西湖区(示范A)","parent_id",0L),
            Map.of("id",2L,"area_code","A001-01","area_name","街道1","parent_id",1L),
            Map.of("id",3L,"area_code","B001","area_name","拱墅区(示范B)","parent_id",0L)));
    }
    Map<String,Object> meter(String no,int status,int battery,int signal) {
        return new HashMap<>(Map.of("meter_no",no,"status",status,"battery_level",battery,"signal_strength",signal,"last_reading_time",LocalDateTime.of(2026,3,31,10,0),"open_count",0,"severe_count",0));
    }
    @Test void inspectionRanksRealFaultsAndExplainsMissingData() {
        var missing=meter("WM-UNKNOWN",0,100,100);missing.remove("last_reading_time");missing.remove("battery_level");
        when(repository.meters(List.of(),null)).thenReturn(List.of(meter("WM-OK",0,100,100),missing,meter("WM-LOW",0,10,20),meter("WM-FAULT",1,100,100)));
        var answer=service.answer("哪些水表需要优先巡检？");
        assertThat(answer.get("intent")).isEqualTo("inspection");
        var rows=(List<Map<String,Object>>)answer.get("rows");
        assertThat(rows).extracting(r->r.get("meterNo")).containsExactly("WM-FAULT","WM-LOW","WM-UNKNOWN");
        assertThat(rows.get(2).get("reasons").toString()).contains("数据缺失","尚无抄表");
        assertThat(answer.toString()).doesNotContain("report_id","故障概率 95");
    }
    @Test void scopedLeakUsesChildrenAndDoesNotDiagnoseLeak() {
        when(repository.leakEvidence(List.of(1L,2L),null)).thenReturn(List.of());
        var answer=service.answer("A区是不是漏水？");
        assertThat(answer.get("scope").toString()).contains("西湖区");
        assertThat(answer.get("summary").toString()).contains("不能证明不存在漏水");
        verify(repository).leakEvidence(List.of(1L,2L),null);
    }
    @Test void scopeDoesNotDropMeterOrUnknownArea() {
        service.answer("WM-A001-0312需要巡检吗？");
        verify(repository).meters(List.of(),"WM-A001-0312");
        for(String q:List.of("Z区是否漏水？","A区和B区是否漏水？","A区和Z区是否漏水？","火星区哪些水表需要巡检？","A区收入多少？","张三欠费多少？","居民收入有多少？","电量低于10%的水表需要巡检？"))
            assertThat(service.answer(q).get("status")).as(q).isEqualTo("clarification");
        verify(repository,never()).leakEvidence(anyList(),any());
    }
    @Test void unsupportedDatesTopicsAndCommandsRequireClarification() {
        for(String q:List.of("明天天气如何？","去年收入多少？","前天收入多少？","本季度收入多少？","今天收入多少？","本月哪些表需要巡检？","2025-01-01哪些水表需检修？","帮我创建工单巡检","收入和欠费多少？","为什么？"))
            assertThat(service.answer(q).get("status")).as(q).isEqualTo("clarification");
        verify(repository,never()).meters(anyList(),any());
        verify(repository,never()).receipts(any(),any());
    }
    @Test void zeroReceiptsDoesNotAssertFallingIncome() {
        when(repository.receipts(any(),any())).thenReturn(Map.of("amount",BigDecimal.ZERO,"count",0));
        var answer=service.answer("为什么本月水费收入下降？");
        assertThat(answer.get("summary").toString()).contains("无法判断");
        verify(repository).receipts(LocalDateTime.of(2026,3,1,0,0),LocalDateTime.of(2026,3,31,12,0));
        verify(repository).receipts(LocalDateTime.of(2026,2,1,0,0),LocalDateTime.of(2026,2,28,12,0));
    }
    @Test void risingIncomeRejectsFallingPremiseAndUsesDecimalComparison() {
        when(repository.receipts(any(),any())).thenReturn(Map.of("amount",new BigDecimal("150.25"),"count",2),Map.of("amount",new BigDecimal("100.00"),"count",1));
        var answer=service.answer("为什么本月收入下降？");
        assertThat(answer.get("summary").toString()).contains("增加 50.25","50.3%");
        assertThat(answer.get("caveats").toString()).contains("不能据此认定");
    }
    @Test void explicitlyRequestedPreviousMonthComparisonIsSupported() {
        when(repository.receipts(any(),any())).thenReturn(Map.of("amount",BigDecimal.ZERO,"count",0));
        assertThat(service.answer("本月收入与上月同期相比如何？").get("intent")).isEqualTo("revenue");
        verify(repository,times(2)).receipts(any(),any());
    }
    @Test void unpaidIncludesPartialBalancesAndNoIncomeAssumption() {
        when(repository.unpaid(any())).thenReturn(Map.of("count",3,"amount",new BigDecimal("12.34"),"overdue",2));
        assertThat(service.answer("当前欠费多少？").get("summary").toString()).contains("3 笔","12.34 元","2 笔");
        verify(repository,never()).receipts(any(),any());
    }
    @Test void rejectsEmptyAndOversizeQuestions() {
        assertThatIllegalArgumentException().isThrownBy(()->service.answer(" "));
        assertThatIllegalArgumentException().isThrownBy(()->service.answer("巡".repeat(501)));
        verifyNoInteractions(repository);
    }
}
