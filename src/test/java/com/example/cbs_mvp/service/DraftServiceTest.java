package com.example.cbs_mvp.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.Optional;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.example.cbs_mvp.ebay.EbayClient;
import com.example.cbs_mvp.ebay.EbayClientException;
import com.example.cbs_mvp.entity.Candidate;
import com.example.cbs_mvp.entity.EbayDraft;
import com.example.cbs_mvp.entity.PricingResult;
import com.example.cbs_mvp.ops.KillSwitchService;
import com.example.cbs_mvp.repo.CandidateRepository;
import com.example.cbs_mvp.repo.EbayDraftRepository;
import com.example.cbs_mvp.repo.PricingResultRepository;

class DraftServiceTest {

    @Test
    void createDraft_success_setsStateToCreated() {
        CandidateRepository candidateRepo = mock(CandidateRepository.class);
        PricingResultRepository pricingRepo = mock(PricingResultRepository.class);
        EbayDraftRepository draftRepo = mock(EbayDraftRepository.class);
        EbayClient ebayClient = mock(EbayClient.class);
        KillSwitchService killSwitch = mock(KillSwitchService.class);
        StateTransitionService transitions = mock(StateTransitionService.class);
        CandidateStateMachine stateMachine = mock(CandidateStateMachine.class);

        DraftService service = new DraftService(
                candidateRepo, pricingRepo, draftRepo, ebayClient, killSwitch, transitions, stateMachine);

        Candidate candidate = new Candidate();
        candidate.setCandidateId(1L);
        candidate.setState("DRAFT_READY");

        PricingResult pricing = new PricingResult();
        pricing.setSellPriceUsd(new BigDecimal("100.00"));

        when(killSwitch.isPaused()).thenReturn(false);
        when(candidateRepo.findById(1L)).thenReturn(Optional.of(candidate));

        // Freshness Check setup
        candidate.setLastCalculatedAt(LocalDateTime.now());
        candidate.setUpdatedAt(LocalDateTime.now());

        when(pricingRepo.findByCandidateId(1L)).thenReturn(Optional.of(pricing));
        when(draftRepo.findBySku("CAND-1")).thenReturn(Optional.empty());
        when(draftRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(candidateRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(ebayClient.createOffer(anyString(), anyMap())).thenReturn("OFFER-123");

        EbayDraft result = service.createDraft(1L);

        assertEquals("EBAY_DRAFT_CREATED", result.getState());
        assertEquals("CAND-1", result.getSku());
        assertEquals("OFFER-123", result.getOfferId());
        assertEquals("EBAY_DRAFT_CREATED", candidate.getState());
    }

    @Test
    void createDraft_ebayFailure_setsStateToFailed() {
        CandidateRepository candidateRepo = mock(CandidateRepository.class);
        PricingResultRepository pricingRepo = mock(PricingResultRepository.class);
        EbayDraftRepository draftRepo = mock(EbayDraftRepository.class);
        EbayClient ebayClient = mock(EbayClient.class);
        KillSwitchService killSwitch = mock(KillSwitchService.class);
        StateTransitionService transitions = mock(StateTransitionService.class);
        CandidateStateMachine stateMachine = mock(CandidateStateMachine.class);

        DraftService service = new DraftService(
                candidateRepo, pricingRepo, draftRepo, ebayClient, killSwitch, transitions, stateMachine);

        Candidate candidate = new Candidate();
        candidate.setCandidateId(2L);
        candidate.setState("DRAFT_READY");

        PricingResult pricing = new PricingResult();
        pricing.setSellPriceUsd(new BigDecimal("50.00"));

        when(killSwitch.isPaused()).thenReturn(false);
        when(candidateRepo.findById(2L)).thenReturn(Optional.of(candidate));

        // Freshness Check setup
        candidate.setLastCalculatedAt(LocalDateTime.now());
        candidate.setUpdatedAt(LocalDateTime.now());

        when(pricingRepo.findByCandidateId(2L)).thenReturn(Optional.of(pricing));
        when(draftRepo.findBySku("CAND-2")).thenReturn(Optional.empty());
        when(draftRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(candidateRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new EbayClientException("API error", false))
                .when(ebayClient).putInventoryItem(anyString(), anyMap());

        EbayDraft result = service.createDraft(2L);

        assertEquals("EBAY_DRAFT_FAILED", result.getState());
        assertEquals("EBAY_DRAFT_FAILED", candidate.getState());
        assertNotNull(result.getLastError());
    }

    @Test
    void createDraft_systemPaused_throwsException() {
        CandidateRepository candidateRepo = mock(CandidateRepository.class);
        PricingResultRepository pricingRepo = mock(PricingResultRepository.class);
        EbayDraftRepository draftRepo = mock(EbayDraftRepository.class);
        EbayClient ebayClient = mock(EbayClient.class);
        KillSwitchService killSwitch = mock(KillSwitchService.class);
        StateTransitionService transitions = mock(StateTransitionService.class);
        CandidateStateMachine stateMachine = mock(CandidateStateMachine.class);

        DraftService service = new DraftService(
                candidateRepo, pricingRepo, draftRepo, ebayClient, killSwitch, transitions, stateMachine);

        when(killSwitch.isPaused()).thenReturn(true);

        assertThrows(IllegalStateException.class, () -> service.createDraft(1L));
    }

    @Test
    void createDraft_idempotent_returnsExistingDraft() {
        CandidateRepository candidateRepo = mock(CandidateRepository.class);
        PricingResultRepository pricingRepo = mock(PricingResultRepository.class);
        EbayDraftRepository draftRepo = mock(EbayDraftRepository.class);
        EbayClient ebayClient = mock(EbayClient.class);
        KillSwitchService killSwitch = mock(KillSwitchService.class);
        StateTransitionService transitions = mock(StateTransitionService.class);
        CandidateStateMachine stateMachine = mock(CandidateStateMachine.class);

        DraftService service = new DraftService(
                candidateRepo, pricingRepo, draftRepo, ebayClient, killSwitch, transitions, stateMachine);

        Candidate candidate = new Candidate();
        candidate.setCandidateId(3L);
        candidate.setState("EBAY_DRAFT_CREATED");

        PricingResult pricing = new PricingResult();
        pricing.setSellPriceUsd(new BigDecimal("75.00"));

        EbayDraft existingDraft = new EbayDraft();
        existingDraft.setDraftId(99L);
        existingDraft.setSku("CAND-3");
        existingDraft.setState("EBAY_DRAFT_CREATED");
        existingDraft.setOfferId("OFFER-EXISTING");

        when(killSwitch.isPaused()).thenReturn(false);
        when(candidateRepo.findById(3L)).thenReturn(Optional.of(candidate));

        // Freshness Check setup
        candidate.setLastCalculatedAt(LocalDateTime.now());
        candidate.setUpdatedAt(LocalDateTime.now());

        when(pricingRepo.findByCandidateId(3L)).thenReturn(Optional.of(pricing));
        when(draftRepo.findBySku("CAND-3")).thenReturn(Optional.of(existingDraft));

        EbayDraft result = service.createDraft(3L);

        assertEquals("EBAY_DRAFT_CREATED", result.getState());
        assertEquals("OFFER-EXISTING", result.getOfferId());
        // eBay API should not be called
        verify(ebayClient, never()).putInventoryItem(anyString(), anyMap());
    }
}
