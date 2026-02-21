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

        Mockito.when(flags.get("CURRENT_CASH")).thenReturn("200000");
        Mockito.when(flags.get("REQUIRED_CASH_BUFFER")).thenReturn("50000");
        Mockito.when(flags.get("REFUND_FIX_RES")).thenReturn("5000");
        Mockito.when(flags.get("RECENT_SALES_30D")).thenReturn("100000");
        Mockito.when(flags.get("REFUND_RES_RATIO")).thenReturn("0.10");
        Mockito.when(poRepo.calculateOpenCommitments()).thenReturn(BigDecimal.ZERO);

        // Available = 200000 - 0 - 10000(reserve) = 190000
        // Threshold = 50000 + 10000 = 60000
        // 190000 >= 60000 => OK
        GateService service = new GateService(flags, poRepo);
        GateResult result = service.checkCashGate(new BigDecimal("10000"));

        assertTrue(result.isOk());
    }

    @Test
    void checkCashGate_insufficientAvailable_returnsFalse() {
        SystemFlagService flags = Mockito.mock(SystemFlagService.class);
        PurchaseOrderRepository poRepo = Mockito.mock(PurchaseOrderRepository.class);

        Mockito.when(flags.get("CURRENT_CASH")).thenReturn("30000");
        Mockito.when(flags.get("REQUIRED_CASH_BUFFER")).thenReturn("50000");
        Mockito.when(flags.get("REFUND_FIX_RES")).thenReturn("0");
        Mockito.when(flags.get("RECENT_SALES_30D")).thenReturn("100000");
        Mockito.when(flags.get("REFUND_RES_RATIO")).thenReturn("0.10");
        Mockito.when(poRepo.calculateOpenCommitments()).thenReturn(BigDecimal.ZERO);

        // Available = 30000 - 0 - 10000(reserve) = 20000
        // Threshold = 50000 + 5000 = 55000
        // 20000 < 55000 => NG
        GateService service = new GateService(flags, poRepo);
        GateResult result = service.checkCashGate(new BigDecimal("5000"));

        assertFalse(result.isOk());
    }

    @Test
    void checkCashGate_withOpenCommitments_reducesAvailable() {
        SystemFlagService flags = Mockito.mock(SystemFlagService.class);
        PurchaseOrderRepository poRepo = Mockito.mock(PurchaseOrderRepository.class);

        Mockito.when(flags.get("CURRENT_CASH")).thenReturn("100000");
        Mockito.when(flags.get("REQUIRED_CASH_BUFFER")).thenReturn("50000");
        Mockito.when(flags.get("REFUND_FIX_RES")).thenReturn("0");
        Mockito.when(flags.get("RECENT_SALES_30D")).thenReturn("0");
        Mockito.when(flags.get("REFUND_RES_RATIO")).thenReturn("0.10");
        Mockito.when(poRepo.calculateOpenCommitments()).thenReturn(new BigDecimal("60000"));

        // Available = 100000 - 60000 - 0(reserve) = 40000
        // Threshold = 50000 + 10000 = 60000
        // 40000 < 60000 => NG
        GateService service = new GateService(flags, poRepo);
        GateResult result = service.checkCashGate(new BigDecimal("10000"));

        assertFalse(result.isOk());
        assertEquals(new BigDecimal("60000"), result.getOpenCommitments());
    }
}
