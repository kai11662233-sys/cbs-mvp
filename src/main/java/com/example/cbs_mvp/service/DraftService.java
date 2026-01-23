package com.example.cbs_mvp.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.cbs_mvp.ebay.EbayClient;
import com.example.cbs_mvp.ebay.EbayClientException;
import com.example.cbs_mvp.entity.Candidate;
import com.example.cbs_mvp.entity.EbayDraft;
import com.example.cbs_mvp.entity.PricingResult;
import com.example.cbs_mvp.ops.KillSwitchService;
import com.example.cbs_mvp.repo.CandidateRepository;
import com.example.cbs_mvp.repo.EbayDraftRepository;
import com.example.cbs_mvp.repo.PricingResultRepository;

@Service
public class DraftService {

    private static final Logger log = LoggerFactory.getLogger(DraftService.class);

    private final CandidateRepository candidateRepo;
    private final PricingResultRepository pricingRepo;
    private final EbayDraftRepository draftRepo;
    private final EbayClient ebayClient;
    private final KillSwitchService killSwitch;
    private final StateTransitionService transitions;

    public DraftService(
            CandidateRepository candidateRepo,
            PricingResultRepository pricingRepo,
            EbayDraftRepository draftRepo,
            EbayClient ebayClient,
            KillSwitchService killSwitch,
            StateTransitionService transitions) {
        this.candidateRepo = candidateRepo;
        this.pricingRepo = pricingRepo;
        this.draftRepo = draftRepo;
        this.ebayClient = ebayClient;
        this.killSwitch = killSwitch;
        this.transitions = transitions;
    }

    @Transactional
    public EbayDraft createDraft(Long candidateId) {
        if (killSwitch.isPaused()) {
            throw new IllegalStateException("system is paused");
        }

        Candidate c = candidateRepo.findById(candidateId)
                .orElseThrow(() -> new IllegalArgumentException("candidate not found"));

        if (!"DRAFT_READY".equals(c.getState()) && !"EBAY_DRAFT_FAILED".equals(c.getState())
                && !"EBAY_DRAFT_CREATED".equals(c.getState())) {
            throw new IllegalArgumentException("candidate not ready");
        }

        PricingResult pr = pricingRepo.findByCandidateId(candidateId)
                .orElseThrow(() -> new IllegalArgumentException("pricing result not found"));

        // Freshness Check (Pricing must be newer than Candidate update, with small
        // buffer)
        // c.updatedAt is likely slightly after pr.createdAt due to priceCandidate logic
        // ordering
        long diffMillis = java.time.Duration.between(pr.getCreatedAt(), c.getUpdatedAt()).toMillis();
        if (diffMillis > 2000) { // If candidate updated more than 2s after pricing
            throw new IllegalStateException("pricing result is stale (candidate modified after pricing)");
        }

        String sku = "CAND-" + candidateId;

        EbayDraft draft = draftRepo.findBySku(sku).orElseGet(EbayDraft::new);
        if (draft.getDraftId() == null) {
            draft.setCandidateId(candidateId);
            draft.setSku(sku);
            draft.setMarketplace("EBAY_US");
            draft.setTitleEn("Candidate " + candidateId);
            draft.setDescriptionHtml("<p>Draft for candidate " + candidateId + "</p>");
            draft.setListPriceUsd(pr.getSellPriceUsd());
            draft.setQuantity(1);
        }

        if ("EBAY_DRAFT_CREATED".equals(draft.getState()) && "EBAY_DRAFT_CREATED".equals(c.getState())) {
            return draft;
        }

        String fromState = c.getState();

        try {
            ebayClient.putInventoryItem(sku, inventoryPayload(sku, pr.getSellPriceUsd()));
            draft.setInventoryItemId("INV-" + sku);

            if (draft.getOfferId() == null || draft.getOfferId().isBlank()) {
                String offerId = ebayClient.createOffer(sku, offerPayload(sku, pr.getSellPriceUsd()));
                draft.setOfferId(offerId);
            }

            draft.setState("EBAY_DRAFT_CREATED");
            draft.setLastError(null);
            draftRepo.save(draft);

            c.setState("EBAY_DRAFT_CREATED");
            c.setRejectReasonCode(null);
            c.setRejectReasonDetail(null);
            candidateRepo.save(c);

            transitions.log("CANDIDATE", candidateId, fromState, c.getState(), null, null, "SYSTEM", cid());
            return draft;
        } catch (EbayClientException ex) {
            if (ex.isOfferError() && draft.getOfferId() != null && !draft.getOfferId().isBlank()) {
                boolean exists = ebayClient.checkOfferExists(draft.getOfferId());
                if (!exists) {
                    draft.setOfferId(null);
                }
            }

            draft.setState("EBAY_DRAFT_FAILED");
            draft.setLastError(ex.getMessage());
            draftRepo.save(draft);

            c.setState("EBAY_DRAFT_FAILED");
            c.setRejectReasonCode("EBAY_DRAFT_FAILED");
            c.setRejectReasonDetail(ex.getMessage());
            candidateRepo.save(c);

            transitions.log("CANDIDATE", candidateId, fromState, c.getState(),
                    c.getRejectReasonCode(), c.getRejectReasonDetail(), "SYSTEM", cid());
            return draft;
        }
    }

    public EbayDraft getDraftByCandidateId(Long candidateId) {
        return draftRepo.findByCandidateId(candidateId).orElse(null);
    }

    public int processReadyCandidates(int limit) {
        if (killSwitch.isPaused()) {
            log.warn("Draft batch skipped: system paused");
            return 0;
        }

        List<Candidate> candidates = candidateRepo.findByStateIn(
                List.of("DRAFT_READY", "EBAY_DRAFT_FAILED"),
                PageRequest.of(0, limit));
        int success = 0;
        for (Candidate c : candidates) {
            try {
                EbayDraft draft = createDraft(c.getCandidateId());
                if ("EBAY_DRAFT_CREATED".equals(draft.getState())) {
                    success++;
                }
            } catch (Exception ex) {
                log.error("Draft creation failed for candidateId={}", c.getCandidateId(), ex);
            }
        }
        return success;
    }

    private static Map<String, Object> inventoryPayload(String sku, BigDecimal priceUsd) {
        return Map.of(
                "sku", sku,
                "priceUsd", priceUsd);
    }

    private static Map<String, Object> offerPayload(String sku, BigDecimal priceUsd) {
        return Map.of(
                "sku", sku,
                "offerPriceUsd", priceUsd);
    }

    private static String cid() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
