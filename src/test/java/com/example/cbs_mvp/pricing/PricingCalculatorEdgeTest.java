package com.example.cbs_mvp.pricing;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Sort;

import com.example.cbs_mvp.ops.SystemFlagService;
import com.example.cbs_mvp.repo.PricingRuleRepository;

class PricingCalculatorEdgeTest {

    private PricingCalculator calculator;

    @Mock
    private SystemFlagService flags;

    @Mock
    private PricingRuleRepository ruleRepo;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(flags.get(anyString())).thenReturn(null); // Default to null to trigger fallbacks
        when(ruleRepo.findAll(any(Sort.class))).thenReturn(Collections.emptyList());

        ShipCostTable shipTable = new ShipCostTable();
        calculator = new PricingCalculator(shipTable, flags, ruleRepo);
    }

    @Test
    void calculate_withNullOptionalInputs_usesDefaults() {
        PricingRequest request = new PricingRequest();
        request.setSourcePriceYen(new BigDecimal("1000"));
        request.setFxRate(new BigDecimal("140"));
        // weightKg and sizeTier are null

        PricingResponse response = calculator.calculate(request);

        assertNotNull(response);
        assertEquals(0, new BigDecimal("1.500").compareTo(response.getSafeWeightKg()), "Should use default weight");
        assertEquals("XL", response.getSafeSizeTier());
        assertTrue(response.isGateProfitOk(), "Recommended price should satisfy gates");
    }

    @Test
    void calculate_withZeroSourcePrice_handlesGracefully() {
        PricingRequest request = new PricingRequest();
        request.setSourcePriceYen(BigDecimal.ZERO);
        request.setFxRate(new BigDecimal("140"));
        request.setWeightKg(new BigDecimal("0.5"));
        request.setSizeTier("S");

        PricingResponse response = calculator.calculate(request);

        // Even with 0 cost, the calculator suggests a price that yields profitMinYen
        // Min profit 3000 yen
        assertTrue(response.getExpectedProfitJpy().compareTo(new BigDecimal("3000")) >= 0,
                "Profit should be at least 3000 yen");
        assertTrue(response.isGateProfitOk());
    }

    @Test
    void calculate_withExtremeFxRate_calculatesCorrectUsd() {
        PricingRequest request = new PricingRequest();
        request.setSourcePriceYen(new BigDecimal("10000"));
        request.setFxRate(new BigDecimal("1.0")); // Extreme scenario: 1 JPY = 1 USD (before buffer)
        request.setWeightKg(new BigDecimal("1.0"));
        request.setSizeTier("M");

        PricingResponse response = calculator.calculate(request);

        // Required profit will be large in USD if FX is 1.0 + 3% buffer
        assertEquals(0, new BigDecimal("10000").compareTo(response.getCalcSourcePriceYen()));
        assertEquals(0, new BigDecimal("1.0300").compareTo(response.getFxSafe()));
    }

    @Test
    void calculate_withCustomTargetSell_respectsItEvenIfLow() {
        PricingRequest request = new PricingRequest();
        request.setSourcePriceYen(new BigDecimal("5000"));
        request.setFxRate(new BigDecimal("150"));
        request.setTargetSellUsd(new BigDecimal("10.00")); // Intentionally very low

        PricingResponse response = calculator.calculate(request);

        assertEquals(0, new BigDecimal("10.00").compareTo(response.getUseSellUsd()));
        assertEquals("⚠️Price Low", response.getWarn());
        assertFalse(response.isGateProfitOk());
    }

    @Test
    void calculate_withInvalidSizeTier_fallsBackToXL() {
        PricingRequest request = new PricingRequest();
        request.setSourcePriceYen(new BigDecimal("5000"));
        request.setFxRate(new BigDecimal("150"));
        request.setSizeTier("INVALID_TIER");

        PricingResponse response = calculator.calculate(request);

        assertEquals("INVALID_TIER", response.getSafeSizeTier());
        // ShipCostTable.costYen("INVALID_TIER") should use XL prices
        BigDecimal intlShip = response.getIntlShipCostYen();
        BigDecimal weight = response.getSafeWeightKg(); // 1.500
        BigDecimal expected = new BigDecimal("3500").add(new BigDecimal("2000").multiply(weight));
        assertEquals(0, expected.compareTo(intlShip));
    }
}
