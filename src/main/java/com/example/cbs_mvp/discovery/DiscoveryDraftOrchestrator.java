package com.example.cbs_mvp.discovery;

import java.math.BigDecimal;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.cbs_mvp.entity.Candidate;
import com.example.cbs_mvp.entity.EbayDraft;
import com.example.cbs_mvp.ops.KillSwitchService;
import com.example.cbs_mvp.service.CandidateService;
import com.example.cbs_mvp.service.DraftService;
import com.example.cbs_mvp.service.StateTransitionService;

/**
 * Discovery → Candidate → Pricing → Gate → Draft の一括処理
 */
@Service
public class DiscoveryDraftOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(DiscoveryDraftOrchestrator.class);

    private final DiscoveryService discoveryService;
    private final DiscoveryItemRepository discoveryRepo;
    private final CandidateService candidateService;
    private final DraftService draftService;
    private final KillSwitchService killSwitch;
    private final StateTransitionService transitions;

    public DiscoveryDraftOrchestrator(
            DiscoveryService discoveryService,
            DiscoveryItemRepository discoveryRepo,
            CandidateService candidateService,
            DraftService draftService,
            KillSwitchService killSwitch,
            StateTransitionService transitions) {
        this.discoveryService = discoveryService;
        this.discoveryRepo = discoveryRepo;
        this.candidateService = candidateService;
        this.draftService = draftService;
        this.killSwitch = killSwitch;
        this.transitions = transitions;
    }

    /**
     * DiscoveryItemからDraftを作成
     * 
     * @return DraftFromDiscoveryResult
     * @throws DraftConditionException 条件不足時
     */
    @Transactional
    public DraftFromDiscoveryResult createDraft(Long discoveryId, BigDecimal fxRate, BigDecimal targetSellUsd) {
        DiscoveryItem item = discoveryRepo.findById(discoveryId)
                .orElseThrow(() -> new IllegalArgumentException("DiscoveryItem not found: " + discoveryId));

        // 冪等性チェック: 既にDraft済みならそれを返す
        if (item.getLinkedDraftId() != null) {
            log.info("DiscoveryItem {} already drafted, returning existing draftId={}",
                    discoveryId, item.getLinkedDraftId());
            return new DraftFromDiscoveryResult(
                    discoveryId,
                    item.getLinkedCandidateId(),
                    null, // pricingResultIdは取得しない
                    item.getLinkedDraftId(),
                    "ALREADY_DRAFTED",
                    "既にDraft済みです");
        }

        // 1) KillSwitchチェック
        if (killSwitch.isPaused()) {
            throw new DraftConditionException("SYSTEM_PAUSED", "システムが一時停止中です: " + killSwitch.getReason());
        }

        // 2) refresh強制実行
        item = discoveryService.refresh(discoveryId);

        // 3) 禁止カテゴリチェック
        if (item.hasRestrictedCategory()) {
            throw new DraftConditionException("RESTRICTED_CATEGORY",
                    "禁止カテゴリに該当するためDraft不可です: " + item.getCategoryHint());
        }

        // 4) Safety閾値チェック（デフォルト50）
        int minSafety = 50; // TODO: system_flagsから取得
        if (item.getSafetyScore() < minSafety) {
            throw new DraftConditionException("SAFETY_TOO_LOW",
                    String.format("SafetyScore(%d)が閾値(%d)未満です", item.getSafetyScore(), minSafety));
        }

        // 5) Candidate作成
        BigDecimal weightKg = item.getWeightKg() != null ? item.getWeightKg() : new BigDecimal("1.500");
        String sizeTier = "XL"; // デフォルト
        String memo = "Discovery ID: " + discoveryId + (item.getNotes() != null ? " | " + item.getNotes() : "");

        Candidate candidate = candidateService.createCandidate(
                item.getSourceUrl(),
                item.getPriceYen(),
                weightKg,
                sizeTier,
                memo);
        Long candidateId = candidate.getCandidateId();

        // 6) Pricing実行
        var pricingResult = candidateService.priceCandidate(candidateId, fxRate, targetSellUsd, false);

        // ProfitScore更新
        boolean gateProfitOk = pricingResult.isGateProfitOk();
        boolean gateCashOk = pricingResult.isGateCashOk();
        BigDecimal profitRate = pricingResult.getProfitRate();

        discoveryService.updateProfitScore(discoveryId, profitRate, gateProfitOk, gateCashOk);

        // 7) Gate判定
        if (!gateProfitOk) {
            discoveryService.updateLinks(discoveryId, candidateId, null, "NG");
            throw new DraftConditionException("PROFIT_GATE_FAIL",
                    String.format("Profit Gate不通過 (rate=%.2f%%)",
                            profitRate != null ? profitRate.doubleValue() * 100 : 0));
        }
        if (!gateCashOk) {
            discoveryService.updateLinks(discoveryId, candidateId, null, "NG");
            throw new DraftConditionException("CASH_GATE_FAIL", "Cash Gate不通過");
        }

        // 8) Draft作成
        EbayDraft draft = draftService.createDraft(candidateId);
        Long draftId = draft.getDraftId();

        // 9) Discovery更新
        discoveryService.updateLinks(discoveryId, candidateId, draftId, "DRAFTED");

        // ログ
        transitions.log("DISCOVERY_ITEM", discoveryId, "CHECKED", "DRAFTED",
                null, "candidateId=" + candidateId + ", draftId=" + draftId,
                "SYSTEM", cid());

        log.info("Created Draft from Discovery: discoveryId={}, candidateId={}, draftId={}",
                discoveryId, candidateId, draftId);

        return new DraftFromDiscoveryResult(
                discoveryId,
                candidateId,
                pricingResult.getPricingId(),
                draftId,
                "SUCCESS",
                "Draft作成完了");
    }

    private static String cid() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    // ----- Result / Exception -----

    public record DraftFromDiscoveryResult(
            Long discoveryId,
            Long candidateId,
            Long pricingResultId,
            Long draftId,
            String status,
            String message) {
    }

    public static class DraftConditionException extends RuntimeException {
        private final String code;

        public DraftConditionException(String code, String message) {
            super(message);
            this.code = code;
        }

        public String getCode() {
            return code;
        }
    }
}
