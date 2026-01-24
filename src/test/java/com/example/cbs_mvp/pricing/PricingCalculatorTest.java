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
        var repo = Mockito.mock(com.example.cbs_mvp.repo.PricingRuleRepository.class); // Mock Repo

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

        // Fix: mock rule repo to return empty list or else NPE in Calculator
        Mockito.when(repo.findAll(Mockito.any(org.springframework.data.domain.Sort.class)))
                .thenReturn(java.util.Collections.emptyList());

        ShipCostTable ship = new ShipCostTable();
        PricingCalculator calc = new PricingCalculator(ship, flags, repo); // Constructor updated

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
        var repo = Mockito.mock(com.example.cbs_mvp.repo.PricingRuleRepository.class);

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

        Mockito.when(repo.findAll(Mockito.any(org.springframework.data.domain.Sort.class)))
                .thenReturn(java.util.Collections.emptyList());

        ShipCostTable ship = new ShipCostTable();
        PricingCalculator calc = new PricingCalculator(ship, flags, repo);

        PricingRequest req = new PricingRequest();
        req.setSourcePriceYen(new BigDecimal("1000")); // Cost
        req.setWeightKg(new BigDecimal("0.1"));
        req.setSizeTier("S");

        req.setFxRate(new BigDecimal("100"));
        req.setTargetSellUsd(new BigDecimal("500"));

        PricingResponse res = calc.calculate(req);

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

    @Test
    void testRuleApplication_HighPrice() {
        var flags = Mockito.mock(SystemFlagService.class);
        var repo = Mockito.mock(com.example.cbs_mvp.repo.PricingRuleRepository.class);

        Mockito.when(flags.get("FX_BUFFER")).thenReturn("0.03");
        Mockito.when(flags.get("DOMESTIC_SHIP")).thenReturn("0");
        Mockito.when(flags.get("PACKING_MISC")).thenReturn("0");
        Mockito.when(flags.get("PL_INBOUND")).thenReturn("0");
        Mockito.when(flags.get("PL_PICKPACK")).thenReturn("0");
        Mockito.when(flags.get("EBAY_FEE_RATE")).thenReturn("0.15");
        Mockito.when(flags.get("REFUND_RES_RATE")).thenReturn("0.00");
        Mockito.when(flags.get("PROFIT_MIN_RATE")).thenReturn("0.20"); // Default 20%
        Mockito.when(flags.get("DEFAULT_WEIGHT_KG")).thenReturn("1.0");
        Mockito.when(flags.get("DEFAULT_SIZE_TIER")).thenReturn("M");

        // Mock Rule: Price >= 10000 -> Profit Rate 15% (Lower than default)
        Mockito.when(flags.get("PROFIT_MIN_YEN")).thenReturn("0"); // Set to 0 to test Rate

        com.example.cbs_mvp.entity.PricingRule rule = new com.example.cbs_mvp.entity.PricingRule();
        rule.setConditionType("SOURCE_PRICE");
        rule.setConditionMin(new BigDecimal("10000"));
        rule.setTargetField("PROFIT_MIN_RATE");
        rule.setAdjustmentValue(new BigDecimal("0.15"));

        Mockito.when(repo.findAll(Mockito.any(org.springframework.data.domain.Sort.class)))
                .thenReturn(java.util.Collections.singletonList(rule));

        ShipCostTable ship = new ShipCostTable();
        PricingCalculator calc = new PricingCalculator(ship, flags, repo);

        PricingRequest req = new PricingRequest();
        req.setSourcePriceYen(new BigDecimal("10000")); // Match rule
        req.setWeightKg(new BigDecimal("1.0"));
        req.setSizeTier("S");
        req.setFxRate(new BigDecimal("100"));

        PricingResponse res = calc.calculate(req);

        // Expected Profit: TotalCost * 0.15
        // Cost = 10000 (others 0) + ship(S=2000) = 12000
        // Min Profit = 12000 * 0.15 = 1800
        // Revenue Needed = 12000 + 1800 = 13800 (div by 0.85 approx)
        // If default 20% was used: 12000 * 0.20 = 2400 profit.

        // We verify that profit rate is closer to 0.15 than 0.20 (checking minimum
        // encoded in calculation)
        // Or simply check profitYen.

        // Let's check calculation directly.
        // TotalCost = 12000.
        // ProfitYen should be around 1800 if 0.15 is used.
        // If 0.20 was used, it would be 2400.

        // Wait, profitYen depends on SellPrice...
        // RecSellUsd is calculated based on Required Profit.
        // RecSellUsd -> SellYen -> ProfitYen.
        // So ProfitYen ≈ RequiredProfit (due to reverse calc).

        BigDecimal profit = res.getProfitYen();
        BigDecimal cost = res.getTotalCostYen();
        BigDecimal profitRate = profit.divide(cost, 4, java.math.RoundingMode.HALF_UP);

        if (Math.abs(profitRate.doubleValue() - 0.15) > 0.001) {
            throw new RuntimeException("TEST_DEBUG: Rate=" + profitRate + ", Profit=" + profit + ", Cost=" + cost);
        }
        assertEquals(0.15, profitRate.doubleValue(), 0.001);
    }
}
