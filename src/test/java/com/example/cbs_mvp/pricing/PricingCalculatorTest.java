package com.example.cbs_mvp.pricing;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.example.cbs_mvp.ops.SystemFlagService;

class PricingCalculatorTest {

    @Test
    void matchesSpreadsheetExample() {
        var flags = Mockito.mock(SystemFlagService.class);

        Mockito.when(flags.get("FX_BUFFER")).thenReturn("0.03");
        Mockito.when(flags.get("DOMESTIC_SHIP")).thenReturn("800");
        Mockito.when(flags.get("PACKING_MISC")).thenReturn("300");
        Mockito.when(flags.get("PL_INBOUND")).thenReturn("200");
        Mockito.when(flags.get("PL_PICKPACK")).thenReturn("500");
        Mockito.when(flags.get("EBAY_FEE_RATE")).thenReturn("0.15");
        Mockito.when(flags.get("REFUND_RES_RATE")).thenReturn("0.05");
        Mockito.when(flags.get("PROFIT_MIN_YEN")).thenReturn("3000");
        Mockito.when(flags.get("PROFIT_MIN_RATE")).thenReturn("0.20");
        Mockito.when(flags.get("DEFAULT_WEIGHT_KG")).thenReturn("1.500");
        Mockito.when(flags.get("DEFAULT_SIZE_TIER")).thenReturn("XL");

        ShipCostTable ship = new ShipCostTable();
        PricingCalculator calc = new PricingCalculator(ship, flags);

        PricingRequest req = new PricingRequest();
        req.setSourcePriceYen(new BigDecimal("10000"));
        req.setWeightKg(new BigDecimal("1.5"));
        req.setSizeTier("XL");
        req.setFxRate(new BigDecimal("145"));
        req.setTargetSellUsd(null);

        PricingResponse out = calc.calculate(req);

        // totalCost = 10000 + (800+300+200+500)=11800 + ship(XL:3500+2000*1.5=6500) => 18300
        assertEquals(new BigDecimal("18300.00"), out.getTotalCostYen());

        // recSellUsd = 183.80（Freeze例）
        assertEquals(new BigDecimal("183.80"), out.getRecSellUsd());

        assertTrue(out.isGateProfitOk());
    }
}
