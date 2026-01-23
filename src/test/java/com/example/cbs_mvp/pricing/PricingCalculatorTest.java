package com.example.cbs_mvp.pricing;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.example.cbs_mvp.ops.SystemFlagService;

class PricingCalculatorTest {

    @Test
    void matchesSpreadsheetExample() {
        // ... (existing test code) ...
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

        // totalCost = 10000 + (800+300+200+500)=11800 + ship(XL:3500+2000*1.5=6500) =>
        // 18300
        assertEquals(new BigDecimal("18300.00"), out.getTotalCostYen());

        // recSellUsd = 183.80（Freeze例）
        assertEquals(new BigDecimal("183.80"), out.getRecSellUsd());

        assertTrue(out.isGateProfitOk());
    }

    @Test
    void customTargetSellPrice_updatesProfitRate() {
        var flags = Mockito.mock(SystemFlagService.class);
        Mockito.when(flags.get("FX_BUFFER")).thenReturn("0.03");
        // Simplified costs for clarity
        Mockito.when(flags.get("DOMESTIC_SHIP")).thenReturn("0");
        Mockito.when(flags.get("PACKING_MISC")).thenReturn("0");
        Mockito.when(flags.get("PL_INBOUND")).thenReturn("0");
        Mockito.when(flags.get("PL_PICKPACK")).thenReturn("0");
        Mockito.when(flags.get("EBAY_FEE_RATE")).thenReturn("0.15");
        Mockito.when(flags.get("REFUND_RES_RATE")).thenReturn("0.00");
        Mockito.when(flags.get("PROFIT_MIN_RATE")).thenReturn("0.20");
        Mockito.when(flags.get("DEFAULT_WEIGHT_KG")).thenReturn("1.0");
        Mockito.when(flags.get("DEFAULT_SIZE_TIER")).thenReturn("M");

        ShipCostTable ship = new ShipCostTable();
        // Mocking ship table effectively or just assumption: weight 1.0, Tier M -> cost
        // 0 (if we assume empty map returns 0 or use simple setup)
        // Since ShipCostTable logic is hardcoded in the class (Map), let's rely on
        // standard logic but use source price dominant.
        // Actually, ShipCostTable is just a data holder.

        PricingCalculator calc = new PricingCalculator(ship, flags);

        PricingRequest req = new PricingRequest();
        req.setSourcePriceYen(new BigDecimal("1000")); // Cost
        req.setWeightKg(new BigDecimal("0.1"));
        req.setSizeTier("S"); // S size -> 2000 yen base (approx, checking details not needed if we focus on
                              // math)
        // Let's use SourcePrice 10,000 and 0 shipping to ensure logic.
        // But ShipCostTable is hard to mock perfectly without changing it.
        // Let's just calculate backwards.

        req.setFxRate(new BigDecimal("100")); // 1 USD = 100 JPY (Safe FX = 103)
        // Total Cost calculation runs inside.
        // Let's assume Total Cost comes out to X.

        // We set Target Sell USD significantly HIGH.
        req.setTargetSellUsd(new BigDecimal("500")); // $500 * 103 = 51500 Yen (Sales)

        PricingResponse res = calc.calculate(req);

        // If we used default logic, it would target ~20%.
        // With $500, profit should be huge.

        assertTrue(res.getProfitRate().compareTo(new BigDecimal("0.20")) > 0,
                "Profit rate should be much higher than 20%");
        assertEquals(new BigDecimal("500"), res.getUseSellUsd(), "Should use the custom target price");

        // Verify WARN logic
        PricingRequest lowReq = new PricingRequest();
        lowReq.setSourcePriceYen(new BigDecimal("10000"));
        lowReq.setFxRate(new BigDecimal("145"));
        lowReq.setTargetSellUsd(new BigDecimal("10")); // Very low

        PricingResponse lowRes = calc.calculate(lowReq);
        assertFalse(lowRes.getWarn().isEmpty(), "Should warn if price is too low");
    }
}
