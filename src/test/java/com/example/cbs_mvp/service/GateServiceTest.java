package com.example.cbs_mvp.service;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.example.cbs_mvp.ops.SystemFlagService;
import com.example.cbs_mvp.repo.PurchaseOrderRepository;

class GateServiceTest {

    @Test
    void checkCashGate_sufficientFunds_returnsOk() {
        SystemFlagService flags = Mockito.mock(SystemFlagService.class);
        PurchaseOrderRepository poRepo = Mockito.mock(PurchaseOrderRepository.class);

        Mockito.when(flags.get("CURRENT_CASH")).thenReturn("100000");
        Mockito.when(flags.get("CREDIT_LIMIT")).thenReturn("50000");
        Mockito.when(flags.get("CREDIT_USED")).thenReturn("0");
        Mockito.when(flags.get("UNCONFIRMED_COST")).thenReturn("0");
        Mockito.when(flags.get("REFUND_FIX_RES")).thenReturn("5000");
        Mockito.when(flags.get("RECENT_SALES_30D")).thenReturn("200000");
        Mockito.when(flags.get("REFUND_RES_RATIO")).thenReturn("0.10");
        Mockito.when(flags.get("WC_CAP_RATIO")).thenReturn("0.30");
        Mockito.when(poRepo.calculateOpenCommitments()).thenReturn(BigDecimal.ZERO);

        GateService service = new GateService(flags, poRepo);
        GateResult result = service.checkCashGate(new BigDecimal("10000"));

        assertTrue(result.isOk());
        assertTrue(result.isCapOk());
    }

    @Test
    void checkCashGate_exceedsCapRatio_returnsFalse() {
        SystemFlagService flags = Mockito.mock(SystemFlagService.class);
        PurchaseOrderRepository poRepo = Mockito.mock(PurchaseOrderRepository.class);

        Mockito.when(flags.get("CURRENT_CASH")).thenReturn("100000");
        Mockito.when(flags.get("CREDIT_LIMIT")).thenReturn("0");
        Mockito.when(flags.get("CREDIT_USED")).thenReturn("0");
        Mockito.when(flags.get("UNCONFIRMED_COST")).thenReturn("0");
        Mockito.when(flags.get("REFUND_FIX_RES")).thenReturn("0");
        Mockito.when(flags.get("RECENT_SALES_30D")).thenReturn("10000"); // 30日売上が少ない
        Mockito.when(flags.get("REFUND_RES_RATIO")).thenReturn("0.10");
        Mockito.when(flags.get("WC_CAP_RATIO")).thenReturn("0.30"); // cap = 3000
        Mockito.when(poRepo.calculateOpenCommitments()).thenReturn(BigDecimal.ZERO);

        GateService service = new GateService(flags, poRepo);
        GateResult result = service.checkCashGate(new BigDecimal("5000")); // 5000 > cap(3000)

        assertFalse(result.isCapOk());
        assertFalse(result.isOk());
    }

    @Test
    void checkCashGate_insufficientWorkingCapital_returnsFalse() {
        SystemFlagService flags = Mockito.mock(SystemFlagService.class);
        PurchaseOrderRepository poRepo = Mockito.mock(PurchaseOrderRepository.class);

        Mockito.when(flags.get("CURRENT_CASH")).thenReturn("10000");
        Mockito.when(flags.get("CREDIT_LIMIT")).thenReturn("0");
        Mockito.when(flags.get("CREDIT_USED")).thenReturn("0");
        Mockito.when(flags.get("UNCONFIRMED_COST")).thenReturn("5000");
        Mockito.when(flags.get("REFUND_FIX_RES")).thenReturn("3000");
        Mockito.when(flags.get("RECENT_SALES_30D")).thenReturn("100000");
        Mockito.when(flags.get("REFUND_RES_RATIO")).thenReturn("0.10"); // reserve = max(3000, 10000) = 10000
        Mockito.when(flags.get("WC_CAP_RATIO")).thenReturn("0.30");
        Mockito.when(poRepo.calculateOpenCommitments()).thenReturn(BigDecimal.ZERO);

        // wcAvailable = 10000 - 0 - 5000 - 10000 - 0 = -5000
        GateService service = new GateService(flags, poRepo);
        GateResult result = service.checkCashGate(new BigDecimal("1000"));

        assertFalse(result.isOk());
    }

    @Test
    void checkCashGate_withOpenCommitments_reducesAvailable() {
        SystemFlagService flags = Mockito.mock(SystemFlagService.class);
        PurchaseOrderRepository poRepo = Mockito.mock(PurchaseOrderRepository.class);

        Mockito.when(flags.get("CURRENT_CASH")).thenReturn("100000");
        Mockito.when(flags.get("CREDIT_LIMIT")).thenReturn("0");
        Mockito.when(flags.get("CREDIT_USED")).thenReturn("0");
        Mockito.when(flags.get("UNCONFIRMED_COST")).thenReturn("0");
        Mockito.when(flags.get("REFUND_FIX_RES")).thenReturn("0");
        Mockito.when(flags.get("RECENT_SALES_30D")).thenReturn("100000");
        Mockito.when(flags.get("REFUND_RES_RATIO")).thenReturn("0.10");
        Mockito.when(flags.get("WC_CAP_RATIO")).thenReturn("0.30"); // cap = 30000
        Mockito.when(poRepo.calculateOpenCommitments()).thenReturn(new BigDecimal("25000"));

        GateService service = new GateService(flags, poRepo);
        // totalCommitments = 25000 + 10000 = 35000 > cap(30000)
        GateResult result = service.checkCashGate(new BigDecimal("10000"));

        assertFalse(result.isCapOk());
        assertEquals(new BigDecimal("25000"), result.getOpenCommitments());
    }
}
