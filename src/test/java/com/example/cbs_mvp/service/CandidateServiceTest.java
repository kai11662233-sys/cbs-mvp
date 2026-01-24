package com.example.cbs_mvp.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.example.cbs_mvp.entity.Candidate;
import com.example.cbs_mvp.entity.PricingResult;
import com.example.cbs_mvp.ops.SystemFlagService;
import com.example.cbs_mvp.pricing.PricingCalculator;
import com.example.cbs_mvp.pricing.PricingRequest;
import com.example.cbs_mvp.pricing.PricingResponse;
import com.example.cbs_mvp.repo.CandidateRepository;

import com.example.cbs_mvp.repo.PricingResultHistoryRepository;
import com.example.cbs_mvp.repo.PricingResultRepository;

class CandidateServiceTest {

        @Test
        void priceCandidate_profitGatePass_setsDraftReady() {
                CandidateRepository candidateRepo = mock(CandidateRepository.class);
                PricingResultRepository pricingRepo = mock(PricingResultRepository.class);
                PricingResultHistoryRepository historyRepo = mock(PricingResultHistoryRepository.class);
                PricingCalculator calculator = mock(PricingCalculator.class);
                GateService gateService = mock(GateService.class);
                SystemFlagService flags = mock(SystemFlagService.class);
                StateTransitionService transitions = mock(StateTransitionService.class);
                DraftService draftService = mock(DraftService.class);

                CandidateService service = new CandidateService(
                                candidateRepo, pricingRepo, historyRepo, calculator, gateService, flags, transitions,
                                draftService);

                Candidate candidate = new Candidate();
                candidate.setCandidateId(1L);
                candidate.setState("CANDIDATE");
                candidate.setSourcePriceYen(new BigDecimal("10000"));
                candidate.setWeightKg(new BigDecimal("1.5"));
                candidate.setSizeTier("M");

                PricingResponse pricingResponse = PricingResponse.builder()
                                .fxSafe(new BigDecimal("150"))
                                .recSellUsd(new BigDecimal("100.00"))
                                .useSellUsd(new BigDecimal("100.00"))
                                .sellYen(new BigDecimal("15000"))
                                .totalCostYen(new BigDecimal("12000"))
                                .profitYen(new BigDecimal("3000"))
                                .profitRate(new BigDecimal("0.25"))
                                .gateProfitOk(true)
                                .build();

                GateResult gateResult = new GateResult(true, true,
                                new BigDecimal("50000"), new BigDecimal("5000"), BigDecimal.ZERO);

                when(candidateRepo.findById(1L)).thenReturn(Optional.of(candidate));
                when(pricingRepo.findByCandidateId(1L)).thenReturn(Optional.empty());
                when(calculator.calculate(any(PricingRequest.class))).thenReturn(pricingResponse);
                when(gateService.checkCashGate(any())).thenReturn(gateResult);
                when(pricingRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
                when(candidateRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

                // Test with autoDraft=false
                PricingResult result = service.priceCandidate(1L, new BigDecimal("145"), null, false);

                assertNotNull(result);
                assertEquals("DRAFT_READY", candidate.getState());
                assertNull(candidate.getRejectReasonCode());
                verify(draftService, never()).createDraft(any());
        }

        @Test
        void priceCandidate_profitGatePass_autoDraft_callsCreateDraft() {
                CandidateRepository candidateRepo = mock(CandidateRepository.class);
                PricingResultRepository pricingRepo = mock(PricingResultRepository.class);
                PricingResultHistoryRepository historyRepo = mock(PricingResultHistoryRepository.class);
                PricingCalculator calculator = mock(PricingCalculator.class);
                GateService gateService = mock(GateService.class);
                SystemFlagService flags = mock(SystemFlagService.class);
                StateTransitionService transitions = mock(StateTransitionService.class);
                DraftService draftService = mock(DraftService.class);

                CandidateService service = new CandidateService(
                                candidateRepo, pricingRepo, historyRepo, calculator, gateService, flags, transitions,
                                draftService);

                Candidate candidate = new Candidate();
                candidate.setCandidateId(4L);
                candidate.setState("CANDIDATE");
                candidate.setSourcePriceYen(new BigDecimal("10000"));
                candidate.setWeightKg(new BigDecimal("1.5"));
                candidate.setSizeTier("M");

                PricingResponse pricingResponse = PricingResponse.builder()
                                .fxSafe(new BigDecimal("150"))
                                .recSellUsd(new BigDecimal("100.00"))
                                .useSellUsd(new BigDecimal("100.00"))
                                .sellYen(new BigDecimal("15000"))
                                .totalCostYen(new BigDecimal("12000"))
                                .profitYen(new BigDecimal("3000"))
                                .profitRate(new BigDecimal("0.25"))
                                .gateProfitOk(true)
                                .build();

                GateResult gateResult = new GateResult(true, true,
                                new BigDecimal("50000"), new BigDecimal("5000"), BigDecimal.ZERO);

                when(candidateRepo.findById(4L)).thenReturn(Optional.of(candidate));
                when(pricingRepo.findByCandidateId(4L)).thenReturn(Optional.empty());
                when(calculator.calculate(any(PricingRequest.class))).thenReturn(pricingResponse);
                when(gateService.checkCashGate(any())).thenReturn(gateResult);
                when(pricingRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
                when(candidateRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

                // Test with autoDraft=true
                PricingResult result = service.priceCandidate(4L, new BigDecimal("145"), null, true);

                assertNotNull(result);
                assertEquals("DRAFT_READY", candidate.getState());
                verify(draftService, times(1)).createDraft(4L);
        }

        @Test
        void priceCandidate_profitGateFail_setsRejected() {
                CandidateRepository candidateRepo = mock(CandidateRepository.class);
                PricingResultRepository pricingRepo = mock(PricingResultRepository.class);
                PricingResultHistoryRepository historyRepo = mock(PricingResultHistoryRepository.class);
                PricingCalculator calculator = mock(PricingCalculator.class);
                GateService gateService = mock(GateService.class);
                SystemFlagService flags = mock(SystemFlagService.class);
                StateTransitionService transitions = mock(StateTransitionService.class);
                DraftService draftService = mock(DraftService.class);

                CandidateService service = new CandidateService(
                                candidateRepo, pricingRepo, historyRepo, calculator, gateService, flags, transitions,
                                draftService);

                Candidate candidate = new Candidate();
                candidate.setCandidateId(2L);
                candidate.setState("CANDIDATE");
                candidate.setSourcePriceYen(new BigDecimal("20000"));
                candidate.setSizeTier("XL");

                PricingResponse pricingResponse = PricingResponse.builder()
                                .fxSafe(new BigDecimal("150"))
                                .recSellUsd(new BigDecimal("80.00"))
                                .useSellUsd(new BigDecimal("80.00"))
                                .sellYen(new BigDecimal("12000"))
                                .totalCostYen(new BigDecimal("18000"))
                                .profitYen(new BigDecimal("500"))
                                .profitRate(new BigDecimal("0.03"))
                                .gateProfitOk(false) // 利益不足
                                .build();

                when(candidateRepo.findById(2L)).thenReturn(Optional.of(candidate));
                when(pricingRepo.findByCandidateId(2L)).thenReturn(Optional.empty());
                when(calculator.calculate(any(PricingRequest.class))).thenReturn(pricingResponse);
                // Fix: mock gateService to return a result (even if we don't care about cash
                // gate for this test, it is called)
                when(gateService.checkCashGate(any())).thenReturn(
                                new GateResult(true, true, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
                when(pricingRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
                when(candidateRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

                PricingResult result = service.priceCandidate(2L, new BigDecimal("145"), null, true);

                assertNotNull(result);
                assertEquals("REJECTED", candidate.getState());
                assertEquals("GATE_PROFIT", candidate.getRejectReasonCode());
                verify(draftService, never()).createDraft(any());
        }

        @Test
        void priceCandidate_cashGateFail_setsRejected() {
                CandidateRepository candidateRepo = mock(CandidateRepository.class);
                PricingResultRepository pricingRepo = mock(PricingResultRepository.class);
                PricingResultHistoryRepository historyRepo = mock(PricingResultHistoryRepository.class);
                PricingCalculator calculator = mock(PricingCalculator.class);
                GateService gateService = mock(GateService.class);
                SystemFlagService flags = mock(SystemFlagService.class);
                StateTransitionService transitions = mock(StateTransitionService.class);
                DraftService draftService = mock(DraftService.class);

                CandidateService service = new CandidateService(
                                candidateRepo, pricingRepo, historyRepo, calculator, gateService, flags, transitions,
                                draftService);

                Candidate candidate = new Candidate();
                candidate.setCandidateId(3L);
                candidate.setState("CANDIDATE");
                candidate.setSourcePriceYen(new BigDecimal("10000"));
                candidate.setSizeTier("M");

                PricingResponse pricingResponse = PricingResponse.builder()
                                .fxSafe(new BigDecimal("150"))
                                .recSellUsd(new BigDecimal("100.00"))
                                .useSellUsd(new BigDecimal("100.00"))
                                .sellYen(new BigDecimal("15000"))
                                .totalCostYen(new BigDecimal("12000"))
                                .profitYen(new BigDecimal("3000"))
                                .gateProfitOk(true)
                                .build();

                GateResult gateResult = new GateResult(false, false, // 資金不足
                                new BigDecimal("1000"), new BigDecimal("5000"), new BigDecimal("50000"));

                when(candidateRepo.findById(3L)).thenReturn(Optional.of(candidate));
                when(pricingRepo.findByCandidateId(3L)).thenReturn(Optional.empty());
                when(calculator.calculate(any(PricingRequest.class))).thenReturn(pricingResponse);
                when(gateService.checkCashGate(any())).thenReturn(gateResult);
                when(pricingRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
                when(candidateRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

                PricingResult result = service.priceCandidate(3L, new BigDecimal("145"), null, true);

                assertNotNull(result);
                assertEquals("REJECTED", candidate.getState());
                assertEquals("GATE_CASH", candidate.getRejectReasonCode());
                verify(draftService, never()).createDraft(any());
        }
}
