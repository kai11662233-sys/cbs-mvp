package com.example.cbs_mvp.discovery;

import java.math.BigDecimal;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
import com.example.cbs_mvp.service.StateTransitionService;
import com.example.cbs_mvp.dto.discovery.CreateDiscoveryItemRequest;

/**
 * Discovery機能のメインサービス
 * - create: 新規登録 + 初期スコア計算
 * - getRecommendations: フィルタ付き一覧
 * - getDetail: 詳細取得
 * - refresh: 再評価
 */
@Service
public class DiscoveryService {

    private static final Logger log = LoggerFactory.getLogger(DiscoveryService.class);

    private final DiscoveryItemRepository repository;
    private final DiscoveryScoringService scoringService;
    private final StateTransitionService transitions;

    public DiscoveryService(DiscoveryItemRepository repository, DiscoveryScoringService scoringService,
            StateTransitionService transitions) {
        this.repository = repository;
        this.scoringService = scoringService;
        this.transitions = transitions;
    }

    /**
     * 新規DiscoveryItem登録
     */
    @Transactional
    public DiscoveryItem create(CreateDiscoveryItemRequest req) {
        DiscoveryItem item = new DiscoveryItem();
        item.setSourceUrl(req.sourceUrl());
        item.setSourceDomain(extractDomain(req.sourceUrl()));
        item.setSourceType(req.sourceType() != null ? req.sourceType() : "OTHER");
        item.setTitle(req.title());
        item.setCondition(req.condition() != null ? req.condition() : "UNKNOWN");
        item.setCategoryHint(req.categoryHint());
        item.setPriceYen(req.priceYen());
        item.setShippingYen(req.shippingYen());
        item.setWeightKg(req.weightKg());
        item.setNotes(req.notes());
        item.setStatus("NEW");
        item.setLastCheckedAt(OffsetDateTime.now());

        // 初期スナップショット
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("initialPriceYen", req.priceYen());
        snapshot.put("registeredAt", OffsetDateTime.now().toString());
        item.setSnapshot(snapshot);

        // 初期スコア計算（Pricingはまだないので profit=0）
        scoringService.recalculateScores(item, null, null, false, false);

        item = repository.save(item);
        log.info("Created DiscoveryItem id={}, title={}", item.getId(), item.getTitle());

        // 監査ログ
        transitions.log("DISCOVERY_ITEM", item.getId(), null, "NEW", null, "Manual Create", "OPs", cid());

        return item;
    }

    private String cid() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * おすすめ一覧取得（フィルタ対応）
     */
    public List<DiscoveryItem> getRecommendations(boolean excludeUsed, int minSafety, int minProfit, int limit) {
        return repository.findRecommendations(excludeUsed, minSafety, minProfit, PageRequest.of(0, limit));
    }

    /**
     * 詳細取得
     */
    public DiscoveryItem getDetail(Long id) {
        return repository.findById(id).orElse(null);
    }

    /**
     * 再評価（refresh）
     * - ルール再適用
     * - 鮮度更新
     * - 価格変動判定
     */
    @Transactional
    public DiscoveryItem refresh(Long id) {
        DiscoveryItem item = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("DiscoveryItem not found: " + id));

        // 前回価格を取得（snapshotから）
        BigDecimal previousPriceYen = null;
        Map<String, Object> snapshot = item.getSnapshot();
        if (snapshot != null && snapshot.containsKey("lastPriceYen")) {
            Object val = snapshot.get("lastPriceYen");
            if (val instanceof Number) {
                previousPriceYen = BigDecimal.valueOf(((Number) val).doubleValue());
            } else if (val instanceof String) {
                previousPriceYen = new BigDecimal((String) val);
            }
        } else if (snapshot != null && snapshot.containsKey("initialPriceYen")) {
            Object val = snapshot.get("initialPriceYen");
            if (val instanceof Number) {
                previousPriceYen = BigDecimal.valueOf(((Number) val).doubleValue());
            } else if (val instanceof BigDecimal) {
                previousPriceYen = (BigDecimal) val;
            }
        }

        // スナップショット更新
        if (snapshot == null) {
            snapshot = new HashMap<>();
        }
        snapshot.put("lastPriceYen", item.getPriceYen());
        snapshot.put("lastCheckedAt", OffsetDateTime.now().toString());
        snapshot.put("previousPriceYen", previousPriceYen);
        item.setSnapshot(snapshot);

        // last_checked_at更新
        item.setLastCheckedAt(OffsetDateTime.now());

        // スコア再計算（現時点ではPricingなしなのでprofit=0のまま）
        // 実際のprofitScoreはDraft処理時に計算される
        scoringService.recalculateScores(item, previousPriceYen, null, false, false);

        // ステータス更新
        if (item.hasRestrictedCategory()) {
            item.setStatus("NG");
        } else if (item.getSafetyScore() < 50) {
            item.setStatus("NG");
        } else {
            item.setStatus("CHECKED");
        }

        item = repository.save(item);
        log.info("Refreshed DiscoveryItem id={}, safetyScore={}, status={}",
                item.getId(), item.getSafetyScore(), item.getStatus());

        return item;
    }

    /**
     * ステータス更新
     */
    @Transactional
    public DiscoveryItem updateStatus(Long id, String status) {
        DiscoveryItem item = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("DiscoveryItem not found: " + id));
        item.setStatus(status);
        return repository.save(item);
    }

    /**
     * リンク情報更新（Candidate/Draft作成後）
     */
    @Transactional
    public DiscoveryItem updateLinks(Long id, Long candidateId, Long draftId, String status) {
        DiscoveryItem item = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("DiscoveryItem not found: " + id));
        item.setLinkedCandidateId(candidateId);
        item.setLinkedDraftId(draftId);
        item.setStatus(status);
        return repository.save(item);
    }

    /**
     * ProfitScore更新（Pricing実行後）
     */
    @Transactional
    public DiscoveryItem updateProfitScore(Long id, BigDecimal profitRate, boolean gateProfitOk, boolean gateCashOk) {
        DiscoveryItem item = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("DiscoveryItem not found: " + id));

        int profitScore = scoringService.calculateProfit(profitRate, gateProfitOk, gateCashOk);
        item.setProfitScore(profitScore);

        // Overall再計算
        int overall = scoringService.calculateOverall(profitScore, item.getSafetyScore(), item.getFreshnessScore());
        item.setOverallScore(overall);

        return repository.save(item);
    }

    /**
     * URLからドメインを抽出
     */
    private String extractDomain(String url) {
        if (url == null || url.isBlank())
            return null;
        try {
            URI uri = new URI(url);
            return uri.getHost();
        } catch (Exception e) {
            return null;
        }
    }
}
