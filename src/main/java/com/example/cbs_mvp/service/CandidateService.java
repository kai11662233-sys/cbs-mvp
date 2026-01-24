package com.example.cbs_mvp.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.cbs_mvp.entity.Candidate;
import com.example.cbs_mvp.entity.PricingResult;
import com.example.cbs_mvp.ops.SystemFlagService;
import com.example.cbs_mvp.pricing.PricingCalculator;
import com.example.cbs_mvp.pricing.PricingRequest;
import com.example.cbs_mvp.pricing.PricingResponse;
import com.example.cbs_mvp.repo.CandidateRepository;
import com.example.cbs_mvp.repo.PricingResultHistoryRepository;
import com.example.cbs_mvp.repo.PricingResultRepository;

@Service
public class CandidateService {

    private final CandidateRepository candidateRepo;
    private final PricingResultRepository pricingRepo;
    private final PricingResultHistoryRepository historyRepo;
    private final PricingCalculator pricingCalculator;
    private final GateService gateService;
    private final SystemFlagService flags;
    private final StateTransitionService transitions;
    private final DraftService draftService;

    public CandidateService(
            CandidateRepository candidateRepo,
            PricingResultRepository pricingRepo,
            PricingResultHistoryRepository historyRepo,
            PricingCalculator pricingCalculator,
            GateService gateService,
            SystemFlagService flags,
            StateTransitionService transitions,
            DraftService draftService) {
        this.candidateRepo = candidateRepo;
        this.pricingRepo = pricingRepo;
        this.historyRepo = historyRepo;
        this.pricingCalculator = pricingCalculator;
        this.gateService = gateService;
        this.flags = flags;
        this.transitions = transitions;
        this.draftService = draftService;
    }

    @Transactional
    public Candidate createCandidate(
            String sourceUrl,
            BigDecimal sourcePriceYen,
            BigDecimal weightKg,
            String sizeTier,
            String memo) {
        Candidate c = new Candidate();
        c.setSourceUrl(sourceUrl);
        c.setSourcePriceYen(sourcePriceYen);
        c.setWeightKg(weightKg);
        c.setSizeTier((sizeTier == null || sizeTier.isBlank()) ? "XL" : sizeTier.trim().toUpperCase());
        c.setMemo(memo);
        c.setState("CANDIDATE");
        c = candidateRepo.save(c);

        transitions.log("CANDIDATE", c.getCandidateId(), null, "CANDIDATE", "CREATE", null, "SYSTEM", cid());
        return c;
    }

    @Transactional
    public PricingResult priceCandidate(Long candidateId, BigDecimal fxRate, BigDecimal targetSellUsd,
            boolean autoDraft) {
        Candidate c = candidateRepo.findById(candidateId)
                .orElseThrow(() -> new IllegalArgumentException("candidate not found"));

        PricingRequest req = new PricingRequest();
        req.setSourcePriceYen(c.getSourcePriceYen());
        req.setWeightKg(c.getWeightKg());
        req.setSizeTier(c.getSizeTier());
        req.setFxRate(fxRate);
        req.setTargetSellUsd(targetSellUsd);

        PricingResponse pr = pricingCalculator.calculate(req);

        GateResult cashGate = gateService.checkCashGate(pr.getTotalCostYen());
        boolean gateCashOk = cashGate.isOk();

        PricingResult result = pricingRepo.findByCandidateId(candidateId).orElseGet(PricingResult::new);
        result.setCandidateId(candidateId);
        result.setFxRate(fxRate);
        result.setFxSafe(pr.getFxSafe());
        result.setSellPriceUsd(pr.getUseSellUsd());
        result.setSellPriceYen(pr.getSellYen());
        result.setTotalCostYen(pr.getTotalCostYen());
        BigDecimal feeYen = calcRate(pr.getSellYen(), flags.get("EBAY_FEE_RATE"), "0.15");
        BigDecimal reserveYen = calcRate(pr.getSellYen(), flags.get("REFUND_RES_RATE"), "0.05");
        BigDecimal profitYen = pr.getSellYen()
                .subtract(pr.getTotalCostYen())
                .subtract(feeYen)
                .subtract(reserveYen)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal profitRate = profitYen
                .divide(pr.getTotalCostYen(), 6, RoundingMode.HALF_UP);

        result.setEbayFeeYen(feeYen);
        result.setRefundReserveYen(reserveYen);
        result.setProfitYen(profitYen);
        result.setProfitRate(profitRate);

        // Snapshot
        result.setCalcSourcePriceYen(pr.getCalcSourcePriceYen());
        result.setCalcWeightKg(pr.getSafeWeightKg());
        result.setCalcIntlShipYen(pr.getIntlShipCostYen());
        result.setUsedFeeRate(pr.getUsedFeeRate());

        result.setGateProfitOk(pr.isGateProfitOk());
        result.setGateCashOk(gateCashOk);

        PricingResult saved = pricingRepo.save(result);

        // --- Save History ---
        saveHistory(candidateId, saved);

        org.slf4j.LoggerFactory.getLogger(CandidateService.class).info(
                "Saved Pricing Snapshot: candidateId={}, weight={}, sourcePrice={}, feeRate={}, ship={}",
                candidateId, result.getCalcWeightKg(), result.getCalcSourcePriceYen(), result.getUsedFeeRate(),
                result.getCalcIntlShipYen());

        String from = c.getState();
        if (pr.isGateProfitOk() && gateCashOk) {
            c.setState("DRAFT_READY");
            c.setRejectReasonCode(null);
            c.setRejectReasonDetail(null);
        } else {
            c.setState("REJECTED");
            c.setRejectReasonCode(reasonCode(pr.isGateProfitOk(), gateCashOk));
            c.setRejectReasonDetail(reasonDetail(pr.isGateProfitOk(), gateCashOk));
        }
        candidateRepo.save(c);
        transitions.log("CANDIDATE", c.getCandidateId(), from, c.getState(), c.getRejectReasonCode(),
                c.getRejectReasonDetail(), "SYSTEM", cid());

        // Auto Draft Logic
        if (autoDraft && pr.isGateProfitOk() && gateCashOk) {
            try {
                draftService.createDraft(candidateId);
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger(CandidateService.class).error("Auto-draft failed for candidateId={}",
                        candidateId, e);
                // Do not re-throw, we still want to return the pricing result
            }
        }

        return saved;
    }

    private void saveHistory(Long candidateId, PricingResult current) {
        com.example.cbs_mvp.entity.PricingResultHistory history = new com.example.cbs_mvp.entity.PricingResultHistory();
        history.setCandidateId(candidateId);
        history.setPricingId(current.getPricingId());
        history.setFxRate(current.getFxRate());
        history.setSellPriceUsd(current.getSellPriceUsd());
        history.setTotalCostYen(current.getTotalCostYen());
        history.setProfitYen(current.getProfitYen());
        history.setProfitRate(current.getProfitRate());
        historyRepo.save(history);
    }

    private static String reasonCode(boolean profitOk, boolean cashOk) {
        if (profitOk && cashOk)
            return null;
        if (!profitOk && !cashOk)
            return "GATE_BOTH";
        return profitOk ? "GATE_CASH" : "GATE_PROFIT";
    }

    private static String reasonDetail(boolean profitOk, boolean cashOk) {
        if (profitOk && cashOk)
            return null;
        if (!profitOk && !cashOk)
            return "profit and cash gate failed";
        return profitOk ? "cash gate failed" : "profit gate failed";
    }

    private static BigDecimal calcRate(BigDecimal base, String rate, String def) {
        BigDecimal r = bd(rate, def);
        return base.multiply(r).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal bd(String s, String def) {
        String v = (s == null || s.isBlank()) ? def : s.trim();
        return new BigDecimal(v);
    }

    @Transactional
    public void recalcAllActiveCandidates(BigDecimal newFxRate) {
        // 対象: CANDIDATE, DRAFT_READY
        java.util.List<String> states = java.util.Arrays.asList("CANDIDATE", "DRAFT_READY");
        java.util.List<Candidate> candidates = candidateRepo.findByStateIn(states,
                org.springframework.data.domain.Pageable.unpaged());

        org.slf4j.LoggerFactory.getLogger(CandidateService.class).info(
                "Starting FX Auto-Recalc for {} candidates. New Rate: {}", candidates.size(), newFxRate);

        for (Candidate c : candidates) {
            try {
                // 既存のPricingResultから以前の販売価格(USD)を取得して維持する
                BigDecimal targetSell = null;
                java.util.Optional<PricingResult> prOpt = pricingRepo.findByCandidateId(c.getCandidateId());
                if (prOpt.isPresent()) {
                    targetSell = prOpt.get().getSellPriceUsd();
                }

                // 再計算 (autoDraft=false: 勝手にドラフトは作らない)
                PricingResult newRes = priceCandidate(c.getCandidateId(), newFxRate, targetSell, false);

                // もしリジェクトされた場合、理由を FX_VOLATILITY (または詳細) に更新したい場合はここで上書き可能
                // ただし priceCandidate 内で既に理由コード(GATE_PROFIT等)がセットされている。
                // FX変動によるものだと明示したいなら、ここでもう一度saveする手もあるが、
                // 一旦はGATE_PROFIT等の標準理由で十分。

                if (!newRes.isGateProfitOk()) {
                    // ログだけ残す
                    org.slf4j.LoggerFactory.getLogger(CandidateService.class).info(
                            "Candidate {} rejected after FX update (Propfit Gate NG). Profit: {}",
                            c.getCandidateId(), newRes.getProfitYen());
                }

            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger(CandidateService.class).error(
                        "Failed to recalc candidate {} during FX update", c.getCandidateId(), e);
            }
        }
    }

    private static String cid() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
