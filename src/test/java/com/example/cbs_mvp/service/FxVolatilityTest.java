package com.example.cbs_mvp.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import com.example.cbs_mvp.entity.Candidate;
import com.example.cbs_mvp.entity.PricingResult;
import com.example.cbs_mvp.ops.SystemFlagService;
import com.example.cbs_mvp.pricing.PricingCalculator;
import com.example.cbs_mvp.pricing.PricingRequest;
import com.example.cbs_mvp.pricing.PricingResponse;
import com.example.cbs_mvp.repo.CandidateRepository;
import com.example.cbs_mvp.repo.PricingResultRepository;

@ExtendWith(MockitoExtension.class)
public class FxVolatilityTest {

    @Mock
    CandidateRepository candidateRepo;
    @Mock
    PricingResultRepository pricingRepo;
    @Mock
    PricingCalculator pricingCalculator;
    @Mock
    GateService gateService;
    @Mock
    SystemFlagService flags;
    @Mock
    StateTransitionService transitions;
    @Mock
    DraftService draftService;

    @InjectMocks
    CandidateService candidateService;

    @BeforeEach
    void setup() {
        // Common mocks
        when(flags.get("EBAY_FEE_RATE")).thenReturn("0.15");
        when(flags.get("REFUND_RES_RATE")).thenReturn("0.05");
    }

    @Test
    public void testRecalc_Success() {
        // 1. Candidate in DRAFT_READY
        Candidate c = new Candidate();
        c.setCandidateId(1L);
        c.setSourcePriceYen(new BigDecimal("1000"));
        c.setWeightKg(new BigDecimal("1.0"));
        c.setState("DRAFT_READY");

        when(candidateRepo.findByStateIn(any(), any(Pageable.class))).thenReturn(Arrays.asList(c));
        when(candidateRepo.findById(1L)).thenReturn(Optional.of(c));

        // 2. PricingResult Mock (Current)
        PricingResult oldPr = new PricingResult();
        oldPr.setSellPriceUsd(new BigDecimal("20.00")); // Previous custom price
        when(pricingRepo.findByCandidateId(1L)).thenReturn(Optional.of(oldPr));

        // 3. Calculator Mock (New Rate = 100.00)
        PricingResponse calcRes = PricingResponse.builder()
                .totalCostYen(new BigDecimal("1500"))
                .useSellUsd(new BigDecimal("20.00"))
                .sellYen(new BigDecimal("2000"))
                .gateProfitOk(true)
                .safeWeightKg(new BigDecimal("1.0"))
                .calcSourcePriceYen(new BigDecimal("1000"))
                .intlShipCostYen(new BigDecimal("500"))
                .build();

        when(pricingCalculator.calculate(any(PricingRequest.class))).thenReturn(calcRes);
        when(gateService.checkCashGate(any())).thenReturn(
                new GateResult(true, true, BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ZERO));

        // Execute
        candidateService.recalcAllActiveCandidates(new BigDecimal("100.00"));

        // Verify
        verify(pricingCalculator).calculate(any(PricingRequest.class));
        verify(candidateRepo).save(c);
        assertEquals("DRAFT_READY", c.getState());
    }

    @Test
    public void testRecalc_Reject() {
        // 1. Candidate in DRAFT_READY
        Candidate c = new Candidate();
        c.setCandidateId(2L);
        c.setSourcePriceYen(new BigDecimal("1000"));
        c.setWeightKg(new BigDecimal("1.0"));
        c.setState("DRAFT_READY");

        when(candidateRepo.findByStateIn(any(), any(Pageable.class))).thenReturn(Arrays.asList(c));
        when(candidateRepo.findById(2L)).thenReturn(Optional.of(c));

        // Mock finding saved result
        when(pricingRepo.findByCandidateId(2L)).thenReturn(Optional.of(new PricingResult()));

        // 3. Calculator Mock (New Rate = 50.00 -> Drastic Drop)
        PricingResponse calcRes = PricingResponse.builder()
                .totalCostYen(new BigDecimal("1500"))
                .useSellUsd(new BigDecimal("20.00"))
                .sellYen(new BigDecimal("1000"))
                .gateProfitOk(false)
                .build();

        when(pricingCalculator.calculate(any(PricingRequest.class))).thenReturn(calcRes);
        when(gateService.checkCashGate(any())).thenReturn(
                new GateResult(true, true, BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ZERO));

        // Execute
        candidateService.recalcAllActiveCandidates(new BigDecimal("50.00"));

        // Verify
        verify(candidateRepo).save(c);
        assertEquals("REJECTED", c.getState());
        assertEquals("GATE_PROFIT", c.getRejectReasonCode());
    }
}
