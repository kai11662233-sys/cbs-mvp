package com.example.cbs_mvp.discovery;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.example.cbs_mvp.dto.discovery.DiscoverySeed;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.cbs_mvp.discovery.DiscoveryDraftOrchestrator.DraftConditionException;
import com.example.cbs_mvp.discovery.DiscoveryDraftOrchestrator.DraftFromDiscoveryResult;
import com.example.cbs_mvp.dto.discovery.CreateDiscoveryItemRequest;
import com.example.cbs_mvp.dto.discovery.DraftRequest;
import com.example.cbs_mvp.ops.OpsKeyService;

import jakarta.validation.Valid;

/**
 * Discovery API Controller
 * 認可: X-OPS-KEY または JWT
 */
@RestController
@RequestMapping("/discovery")
public class DiscoveryController {

    private final OpsKeyService opsKeyService;
    private final DiscoveryService discoveryService;
    private final DiscoveryDraftOrchestrator orchestrator;
    private final DiscoveryIngestService ingestService;
    private final List<ExternalItemSearchService> searchServices;
    private final AutoRecommendationService autoRecommendationService;
    private final com.example.cbs_mvp.fx.FxRateService fxRateService;
    private final com.example.cbs_mvp.pricing.PricingCalculator pricingCalculator;
    private final com.example.cbs_mvp.service.GateService gateService;

    public DiscoveryController(
            OpsKeyService opsKeyService,
            DiscoveryService discoveryService,
            DiscoveryDraftOrchestrator orchestrator,
            DiscoveryIngestService ingestService,
            List<ExternalItemSearchService> searchServices,
            AutoRecommendationService autoRecommendationService,
            com.example.cbs_mvp.fx.FxRateService fxRateService,
            com.example.cbs_mvp.pricing.PricingCalculator pricingCalculator,
            com.example.cbs_mvp.service.GateService gateService) {
        this.opsKeyService = opsKeyService;
        this.discoveryService = discoveryService;
        this.orchestrator = orchestrator;
        this.ingestService = ingestService;
        this.searchServices = searchServices;
        this.autoRecommendationService = autoRecommendationService;
        this.fxRateService = fxRateService;
        this.pricingCalculator = pricingCalculator;
        this.gateService = gateService;
    }

    /**
     * 1) POST /discovery/items
     * 新規DiscoveryItem登録
     */
    @PostMapping("/items")
    public ResponseEntity<?> createItem(
            @RequestHeader(value = "X-OPS-KEY", required = false) String opsKey,
            @Valid @RequestBody CreateDiscoveryItemRequest body) {

        if (!isAuthorized(opsKey)) {
            return unauthorized();
        }

        DiscoveryItem item = discoveryService.create(body);

        return ResponseEntity.ok(Map.of(
                "id", item.getId(),
                "title", item.getTitle() != null ? item.getTitle() : "",
                "status", item.getStatus(),
                "safetyScore", item.getSafetyScore(),
                "overallScore", item.getOverallScore()));
    }

    /**
     * 2) GET /discovery/recommendations
     * おすすめ一覧取得（フィルタ対応）
     */
    @GetMapping("/recommendations")
    public ResponseEntity<?> getRecommendations(
            @RequestHeader(value = "X-OPS-KEY", required = false) String opsKey,
            @RequestParam(name = "excludeUsed", defaultValue = "true") boolean excludeUsed,
            @RequestParam(name = "minSafety", defaultValue = "0") int minSafety,
            @RequestParam(name = "minProfit", defaultValue = "0") int minProfit,
            @RequestParam(name = "limit", defaultValue = "50") int limit) {

        if (!isAuthorized(opsKey)) {
            return unauthorized();
        }

        List<DiscoveryItem> items = discoveryService.getRecommendations(excludeUsed, minSafety, minProfit, limit);

        List<Map<String, Object>> result = items.stream().map(this::toSummaryDto).toList();

        return ResponseEntity.ok(Map.of(
                "items", result,
                "count", result.size(),
                "filters", Map.of(
                        "excludeUsed", excludeUsed,
                        "minSafety", minSafety,
                        "minProfit", minProfit)));
    }

    /**
     * 3) GET /discovery/items/{id}
     * 詳細取得
     */
    @GetMapping("/items/{id}")
    public ResponseEntity<?> getDetail(
            @RequestHeader(value = "X-OPS-KEY", required = false) String opsKey,
            @PathVariable Long id) {

        if (!isAuthorized(opsKey)) {
            return unauthorized();
        }

        DiscoveryItem item = discoveryService.getDetail(id);
        if (item == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Not found"));
        }

        return ResponseEntity.ok(toDetailDto(item));
    }

    /**
     * 4) POST /discovery/items/{id}/refresh
     * 再評価
     */
    @PostMapping("/items/{id}/refresh")
    public ResponseEntity<?> refresh(
            @RequestHeader(value = "X-OPS-KEY", required = false) String opsKey,
            @PathVariable Long id) {

        if (!isAuthorized(opsKey)) {
            return unauthorized();
        }

        try {
            DiscoveryItem item = discoveryService.refresh(id);
            return ResponseEntity.ok(toDetailDto(item));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 5) POST /discovery/items/{id}/draft
     * Draft作成
     */
    @PostMapping("/items/{id}/draft")
    public ResponseEntity<?> createDraft(
            @RequestHeader(value = "X-OPS-KEY", required = false) String opsKey,
            @PathVariable Long id,
            @Valid @RequestBody(required = false) DraftRequest body) {

        if (!isAuthorized(opsKey)) {
            return unauthorized();
        }

        try {
            BigDecimal fxRate = (body != null && body.fxRate() != null) ? body.fxRate() : new BigDecimal("150.0");
            BigDecimal targetSellUsd = body != null ? body.targetSellUsd() : null;

            DraftFromDiscoveryResult result = orchestrator.createDraft(id, fxRate, targetSellUsd);

            return ResponseEntity.ok(Map.of(
                    "discoveryId", result.discoveryId(),
                    "candidateId", result.candidateId(),
                    "pricingResultId", result.pricingResultId() != null ? result.pricingResultId() : "",
                    "draftId", result.draftId(),
                    "status", result.status(),
                    "message", result.message()));
        } catch (DraftConditionException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "error", e.getCode(),
                    "message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "error", "INVALID_STATE",
                    "message", e.getMessage()));
        }
    }

    /**
     * 6) POST /discovery/external-search?keyword=...
     * 外部サイト(Yahoo/Rakuten)から検索してDiscoveryに登録
     */
    @PostMapping("/external-search")
    public ResponseEntity<?> externalSearch(
            @RequestHeader(value = "X-OPS-KEY", required = false) String opsKey,
            @RequestParam String keyword) {

        if (!isAuthorized(opsKey)) {
            return unauthorized();
        }

        int totalInserted = 0;
        int totalUpdated = 0;
        List<DiscoverySeed> allResults = new ArrayList<>();

        // 各サービスで検索
        for (ExternalItemSearchService service : searchServices) {
            try {
                List<DiscoverySeed> results = service.searchItems(keyword);
                allResults.addAll(results);
            } catch (Exception e) {
                // 個別のエラーはログに出して続行 (e.g. API limit)
                e.printStackTrace();
            }
        }

        // 登録処理
        for (DiscoverySeed seed : allResults) {
            try {
                boolean isNew = ingestService.upsert(seed);
                if (isNew) {
                    totalInserted++;
                } else {
                    totalUpdated++;
                }
            } catch (Exception e) {
                // 無視して次へ
            }
        }

        return ResponseEntity.ok(Map.of(
                "keyword", keyword,
                "found", allResults.size(),
                "inserted", totalInserted,
                "updated", totalUpdated));
    }

    /**
     * 7) POST /discovery/auto-recommend
     * 自動おすすめ取得（キーワード不要・ワンクリック）
     * 価格帯・利益ゲートで自動フィルタして登録
     */
    @PostMapping("/auto-recommend")
    public ResponseEntity<?> autoRecommend(
            @RequestHeader(value = "X-OPS-KEY", required = false) String opsKey) {

        if (!isAuthorized(opsKey)) {
            return unauthorized();
        }

        var result = autoRecommendationService.execute();

        return ResponseEntity.ok(Map.of(
                "fetched", result.fetched(),
                "inserted", result.inserted(),
                "updated", result.updated(),
                "skipped", result.skipped()));
    }

    // ----- Helper methods -----

    private boolean isAuthorized(String opsKey) {
        // OPS-KEY または JWT認証をチェック
        if (opsKeyService.isValid(opsKey)) {
            return true;
        }
        // JWT認証
        var auth = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        return auth != null && auth.isAuthenticated()
                && !(auth instanceof org.springframework.security.authentication.AnonymousAuthenticationToken);
    }

    private ResponseEntity<?> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "X-OPS-KEY or JWT required"));
    }

    private Map<String, Object> toSummaryDto(DiscoveryItem item) {
        return Map.ofEntries(
                Map.entry("id", item.getId()),
                Map.entry("title", item.getTitle() != null ? item.getTitle() : ""),
                Map.entry("sourceUrl", item.getSourceUrl()),
                Map.entry("condition", item.getCondition()),
                Map.entry("priceYen", item.getPriceYen()),
                Map.entry("safetyScore", item.getSafetyScore()),
                Map.entry("profitScore", item.getProfitScore()),
                Map.entry("freshnessScore", item.getFreshnessScore()),
                Map.entry("overallScore", item.getOverallScore()),
                Map.entry("status", item.getStatus()),
                Map.entry("lastCheckedAt", item.getLastCheckedAt() != null ? item.getLastCheckedAt().toString() : ""),
                Map.entry("riskFlags", item.getRiskFlags() != null ? item.getRiskFlags() : List.of()),
                Map.entry("isDraftable", item.isDraftable()));
    }

    private Map<String, Object> toDetailDto(DiscoveryItem item) {
        // Pricing概算を計算
        Map<String, Object> pricingEstimate = calculatePricingEstimate(item);

        var entries = new java.util.LinkedHashMap<String, Object>();
        entries.put("id", item.getId());
        entries.put("sourceUrl", item.getSourceUrl());
        entries.put("sourceDomain", item.getSourceDomain() != null ? item.getSourceDomain() : "");
        entries.put("sourceType", item.getSourceType());
        entries.put("title", item.getTitle() != null ? item.getTitle() : "");
        entries.put("condition", item.getCondition());
        entries.put("categoryHint", item.getCategoryHint() != null ? item.getCategoryHint() : "");
        entries.put("priceYen", item.getPriceYen());
        entries.put("shippingYen", item.getShippingYen() != null ? item.getShippingYen() : "");
        entries.put("weightKg", item.getWeightKg() != null ? item.getWeightKg() : "");
        entries.put("safetyScore", item.getSafetyScore());
        entries.put("profitScore", item.getProfitScore());
        entries.put("freshnessScore", item.getFreshnessScore());
        entries.put("overallScore", item.getOverallScore());
        entries.put("riskFlags", item.getRiskFlags() != null ? item.getRiskFlags() : List.of());
        entries.put("safetyBreakdown", item.getSafetyBreakdown() != null ? item.getSafetyBreakdown() : List.of());
        entries.put("snapshot", item.getSnapshot() != null ? item.getSnapshot() : Map.of());
        entries.put("status", item.getStatus());
        entries.put("linkedCandidateId", item.getLinkedCandidateId() != null ? item.getLinkedCandidateId() : "");
        entries.put("linkedDraftId", item.getLinkedDraftId() != null ? item.getLinkedDraftId() : "");
        entries.put("notes", item.getNotes() != null ? item.getNotes() : "");
        entries.put("lastCheckedAt", item.getLastCheckedAt() != null ? item.getLastCheckedAt().toString() : "");
        entries.put("createdAt", item.getCreatedAt() != null ? item.getCreatedAt().toString() : "");
        entries.put("updatedAt", item.getUpdatedAt() != null ? item.getUpdatedAt().toString() : "");
        entries.put("isDraftable", item.isDraftable());
        entries.put("pricingEstimate", pricingEstimate);
        return entries;
    }

    private Map<String, Object> calculatePricingEstimate(DiscoveryItem item) {
        try {
            com.example.cbs_mvp.fx.FxRateService.FxRateResult fxResult = fxRateService.getCurrentRate();
            java.math.BigDecimal fxRate = fxResult.isSuccess() && fxResult.rate() != null
                    ? fxResult.rate()
                    : new java.math.BigDecimal("150.0");

            com.example.cbs_mvp.pricing.PricingRequest request = new com.example.cbs_mvp.pricing.PricingRequest();
            request.setSourcePriceYen(item.getPriceYen());
            request.setWeightKg(item.getWeightKg());
            request.setSizeTier(null);
            request.setFxRate(fxRate);
            request.setTargetSellUsd(null);

            com.example.cbs_mvp.pricing.PricingResponse response = pricingCalculator.calculate(request);
            com.example.cbs_mvp.service.GateResult gateResult = gateService.checkCashGate(
                    response.getExpectedCostJpy() != null ? response.getExpectedCostJpy() : java.math.BigDecimal.ZERO);

            var estimate = new java.util.LinkedHashMap<String, Object>();
            estimate.put("recSellUsd", response.getRecSellUsd());
            estimate.put("intlShipCostYen", response.getIntlShipCostYen());
            estimate.put("expectedProfitJpy", response.getExpectedProfitJpy());
            estimate.put("expectedCostJpy", response.getExpectedCostJpy());
            estimate.put("profitRate", response.getProfitRate());
            estimate.put("gateProfitOk", response.isGateProfitOk());
            estimate.put("gateCashOk", gateResult.isOk());
            estimate.put("fxRate", fxRate);
            estimate.put("availableCash", gateResult.getAvailableCash());
            estimate.put("requiredCashBuffer", gateResult.getRequiredCashBuffer());
            estimate.put("warn", response.getWarn() != null ? response.getWarn() : "");
            return estimate;
        } catch (Exception e) {
            return Map.of("error", "pricing calculation failed: " + e.getMessage());
        }
    }
}
