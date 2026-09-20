package com.water.ai.meter.service.impl;

import com.water.ai.meter.entity.*;
import com.water.ai.meter.mapper.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BillServiceImplTest {
    BillMapper bills = mock(BillMapper.class);
    WaterMeterMapper meters = mock(WaterMeterMapper.class);
    MeterReadingMapper readings = mock(MeterReadingMapper.class);
    SysUserMapper users = mock(SysUserMapper.class);
    BillPaymentMapper payments = mock(BillPaymentMapper.class);
    PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    BillServiceImpl service;
    MeterReading reading;
    Bill bill;

    @BeforeEach
    void setup() {
        service = new BillServiceImpl(meters, users, readings, payments, transactions, new ObjectMapper(),mock(com.water.ai.meter.tariff.ResidentialTariffService.class));
        when(transactions.getTransaction(any(TransactionDefinition.class))).thenReturn(new SimpleTransactionStatus());
        ReflectionTestUtils.setField(service, "baseMapper", bills);
        ReflectionTestUtils.setField(service, "ladder1", 180);
        ReflectionTestUtils.setField(service, "ladder2", 260);
        for (var entry : Map.of("price1", "2.07", "price2", "3.10", "price3", "4.65",
                "commercialPrice", "4.50", "industrialPrice", "5.80", "sewageRate", "0.9").entrySet()) {
            ReflectionTestUtils.setField(service, entry.getKey(), new BigDecimal(entry.getValue()));
        }
        reading = MeterReading.builder().id(10L).meterId(1L).userId(2L).status(1).deleted(0)
                .readingValue(new BigDecimal("150")).usageAmount(new BigDecimal("30"))
                .readingPeriod("2026-08").readingTime(LocalDateTime.of(2026, 8, 31, 12, 0)).build();
        when(readings.selectById(10L)).thenReturn(reading);
        when(readings.selectForUpdate(10L)).thenReturn(reading);
        when(meters.selectById(1L)).thenReturn(WaterMeter.builder().id(1L).userId(2L).deleted(0).build());
        when(users.selectById(2L)).thenReturn(SysUser.builder().id(2L).userType("residential").deleted(0).build());
        doAnswer(invocation -> { Bill saved = invocation.getArgument(0); saved.setId(100L); return 1; })
                .when(bills).insert(any(Bill.class));
        bill = Bill.builder().id(100L).billNo("TEST").status(0).deleted(0)
                .totalAmount(new BigDecimal("100.00")).paidAmount(BigDecimal.ZERO).build();
        when(bills.selectById(100L)).thenReturn(bill);
        when(bills.selectForUpdate(100L)).thenReturn(bill);
        when(bills.updatePayment(anyLong(), anyInt(), any(), any(), anyString(), anyString())).thenReturn(1);
        doAnswer(invocation -> { BillPayment p = invocation.getArgument(0); p.setId(1L); return 1; })
                .when(payments).insert(any(BillPayment.class));
        when(bills.updateById(any(Bill.class))).thenReturn(1);
    }

    @Test
    void generatesBillFromSavedUsageInsteadOfSubtractingReadingFromItself() {
        assertThat(service.generateBill(1L, 10L).get("success")).isEqualTo(true);
        var captured = ArgumentCaptor.forClass(Bill.class);
        verify(bills).insert(captured.capture());
        Bill saved = captured.getValue();
        assertThat(saved.getUsageAmount()).isEqualByComparingTo("30");
        assertThat(saved.getStartReading()).isEqualByComparingTo("120");
        assertThat(saved.getEndReading()).isEqualByComparingTo("150");
        assertThat(saved.getTotalAmount()).isEqualByComparingTo("117.99");
        assertThat(saved.getBillPeriod()).isEqualTo("2026-08");
    }

    @Test
    void rejectsUnapprovedReadings() {
        reading.setStatus(0);
        assertThat(service.generateBill(1L, 10L).get("success")).isEqualTo(false);
        verify(bills, never()).insert(any(Bill.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "0", "100.01", "0.001"})
    void rejectsInvalidPaymentsWithoutChangingBill(String amount) {
        assertThat(service.payBill(100L, new BigDecimal(amount), "cash", "request-1").get("success"))
                .isEqualTo(false);
        verify(bills, never()).updateById(any(Bill.class));
        assertThat(bill.getPaidAmount()).isEqualByComparingTo("0");
        assertThat(bill.getStatus()).isZero();
        verify(payments, never()).insert(any(BillPayment.class));
        verify(bills, never()).updatePayment(anyLong(),anyInt(),any(),any(),anyString(),anyString());
    }

    @Test
    void partialPaymentMustNotSettleBill() {
        assertThat(service.payBill(100L, new BigDecimal("40"), "cash", "request-1").get("success"))
                .isEqualTo(true);
        assertThat(bill.getPaidAmount()).isEqualByComparingTo("40");
        assertThat(bill.getStatus()).isEqualTo(3);
    }

    @Test
    void paymentAccumulatesAndSettlesOnlyAtFullAmount() {
        service.payBill(100L, new BigDecimal("40"), "cash", "first");
        service.payBill(100L, new BigDecimal("60"), "bank", "second");
        assertThat(bill.getPaidAmount()).isEqualByComparingTo("100");
        assertThat(bill.getStatus()).isEqualTo(1);
        verify(payments, times(2)).insert(any(BillPayment.class));
    }

    @Test
    void replayUsesOriginalReceiptWithoutUpdatingBill() {
        bill.setPaidAmount(new BigDecimal("100")).setStatus(1);
        BillPayment receipt = BillPayment.builder().id(4L).billId(100L).amount(new BigDecimal("100"))
                .payMethod("cash").tradeNo("retry-1").build();
        when(payments.selectByTradeNo("retry-1")).thenReturn(receipt);
        Map<?, ?> data = (Map<?, ?>) service.payBill(100L,new BigDecimal("100"),"cash","retry-1").get("data");
        assertThat(data.get("replayed")).isEqualTo(true);
        assertThat(data.get("payment")).isSameAs(receipt);
        verify(payments, never()).insert(any(BillPayment.class));
        verify(bills, never()).updatePayment(anyLong(),anyInt(),any(),any(),anyString(),anyString());
    }

    @Test
    void conflictingRequestIdCannotChangePayloadOrBill() {
        when(payments.selectByTradeNo("used")).thenReturn(BillPayment.builder().billId(999L)
                .amount(new BigDecimal("40")).payMethod("cash").build());
        assertThat(service.payBill(100L,new BigDecimal("40"),"cash","used").get("success")).isEqualTo(false);
        verify(payments, never()).insert(any(BillPayment.class));
    }

    @Test
    void rejectsInvalidChannelAndMissingRequestId() {
        assertThat(service.payBill(100L, BigDecimal.ONE, "unknown", "id").get("success")).isEqualTo(false);
        assertThat(service.payBill(100L, BigDecimal.ONE, "cash", null).get("success")).isEqualTo(false);
        assertThat(service.payBill(100L, BigDecimal.ONE, "cash", " ").get("success")).isEqualTo(false);
        verifyNoInteractions(payments);
    }

    @Test
    void readingMustBelongToMeterAndHaveValidUsage() {
        reading.setMeterId(5L);
        assertThat(service.generateBill(1L,10L).get("success")).isEqualTo(false);
        reading.setMeterId(1L).setUsageAmount(new BigDecimal("-1"));
        assertThat(service.generateBill(1L,10L).get("success")).isEqualTo(false);
        reading.setUsageAmount(null);
        assertThat(service.generateBill(1L,10L).get("success")).isEqualTo(false);
        verify(bills, never()).insert(any(Bill.class));
    }

    @Test
    void duplicateGenerationReturnsExistingBill() {
        bill.setUsageAmount(new BigDecimal("30"));
        when(bills.selectByReadingId(10L)).thenReturn(List.of(bill));
        Map<?,?> data = (Map<?,?>) service.generateBill(1L,10L).get("data");
        assertThat(data.get("existing")).isEqualTo(true);
        verify(bills, never()).insert(any(Bill.class));
    }

    @Test
    void feeBoundariesAndCommercialIndustrialPrices() throws Exception {
        String[][] cases = {{"residential","180","372.60"},{"residential","260","620.60"},
                {"residential","261","625.25"},{"commercial","30","135.00"},{"industrial","30","174.00"}};
        for (String[] c : cases) {
            clearInvocations(bills);
            reading.setReadingValue(new BigDecimal("1000")).setUsageAmount(new BigDecimal(c[1]));
            when(users.selectById(2L)).thenReturn(SysUser.builder().id(2L).userType(c[0]).build());
            assertThat(service.generateBill(1L,10L).get("success")).isEqualTo(true);
            var saved = ArgumentCaptor.forClass(Bill.class);
            verify(bills).insert(saved.capture());
            assertThat(saved.getValue().getWaterFee()).isEqualByComparingTo(c[2]);
            assertThat(new ObjectMapper().readTree(saved.getValue().getLadderDetail()).isArray()).isTrue();
        }
    }

    @Test
    void batchCountsBusinessFailuresInsteadOfReportingSuccess() {
        reading.setStatus(0);
        Map<?,?> data=(Map<?,?>)service.batchGenerateBills(List.of(10L,999L)).get("data");
        assertThat(data.get("success")).isEqualTo(0);
        assertThat(data.get("failed")).isEqualTo(2);
        verify(bills, never()).insert(any(Bill.class));
    }
}
