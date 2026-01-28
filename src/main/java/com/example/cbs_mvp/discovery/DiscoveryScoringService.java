package com.example.cbs_mvp.discovery;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

/**
 * Discoveryアイテムのスコア計算サービス
 */
@Service
public class DiscoveryScoringService {

    // ===== スコア減点・加点値 =====
    private static final int PENALTY_USED_CONDITION = -20;
    private static final int PENALTY_UNKNOWN_CONDITION = -10;
    private static final int PENALTY_C2C_SOURCE = -25;
    private static final int PENALTY_RESTRICTED_CATEGORY = -40;
    private static final int PENALTY_HIGH_VOLATILITY = -20;
    private static final int PENALTY_VOLATILITY = -10;
    private static final int BONUS_TRUSTED_SOURCE = 10;

    // ===== 閾値 =====
    private static final double VOLATILITY_THRESHOLD_HIGH = 10.0;
    private static final double VOLATILITY_THRESHOLD_LOW = 5.0;

    // ===== Overall重み付け =====
    private static final double WEIGHT_PROFIT = 0.55;
    private static final double WEIGHT_SAFETY = 0.35;
    private static final double WEIGHT_FRESHNESS = 0.10;

    // ===== Freshness時間閾値（時間） =====
    private static final int FRESHNESS_EXCELLENT_HOURS = 1;
    private static final int FRESHNESS_GOOD_HOURS = 6;
    private static final int FRESHNESS_FAIR_HOURS = 24;
    private static final int FRESHNESS_STALE_HOURS = 72;

    // 禁止カテゴリ（Draft不可）
    private static final Set<String> RESTRICTED_CATEGORIES = Set.of(
            "battery", "cosmetic", "medical", "food", "digitalcode", "counterfeit-risk",
            "バッテリー", "化粧品", "医療", "食品", "デジタルコード");

    /**
     * SafetyScore計算（100から減点方式）
     * 
     * @return SafetyResult（score, breakdown, riskFlags）
     */
    public SafetyResult calculateSafety(DiscoveryItem item, BigDecimal previousPriceYen) {
        int score = 100;
        List<Map<String, Object>> breakdown = new ArrayList<>();
        List<String> riskFlags = new ArrayList<>();

        // 1. Condition判定
        if ("USED".equalsIgnoreCase(item.getCondition())) {
            score += PENALTY_USED_CONDITION;
            breakdown.add(breakdownEntry("USED_CONDITION", PENALTY_USED_CONDITION, "中古品"));
            riskFlags.add("USED_CONDITION");
        } else if ("UNKNOWN".equalsIgnoreCase(item.getCondition())) {
            score += PENALTY_UNKNOWN_CONDITION;
            breakdown.add(breakdownEntry("UNKNOWN_CONDITION", PENALTY_UNKNOWN_CONDITION, "状態不明"));
            riskFlags.add("UNKNOWN_CONDITION");
        }

        // 2. SourceType判定
        String srcType = item.getSourceType();
        if ("C2C".equalsIgnoreCase(srcType)) {
            score += PENALTY_C2C_SOURCE;
            breakdown.add(breakdownEntry("C2C_SOURCE", PENALTY_C2C_SOURCE, "C2Cプラットフォーム（メルカリ等）"));
            riskFlags.add("C2C_SOURCE");
        } else if ("OFFICIAL".equalsIgnoreCase(srcType) || "RETAIL".equalsIgnoreCase(srcType)) {
            score += BONUS_TRUSTED_SOURCE;
            breakdown.add(breakdownEntry("TRUSTED_SOURCE", BONUS_TRUSTED_SOURCE, "公式/正規店"));
        }

        // 3. 禁止カテゴリ判定
        String categoryHint = item.getCategoryHint();
        if (categoryHint != null && !categoryHint.isBlank()) {
            String lowerHint = categoryHint.toLowerCase();
            for (String restricted : RESTRICTED_CATEGORIES) {
                if (lowerHint.contains(restricted.toLowerCase())) {
                    score += PENALTY_RESTRICTED_CATEGORY;
                    breakdown.add(breakdownEntry("RESTRICTED_CATEGORY", PENALTY_RESTRICTED_CATEGORY,
                            "禁止カテゴリ: " + restricted));
                    riskFlags.add("RESTRICTED_CATEGORY");
                    break;
                }
            }
        }

        // 4. 価格変動判定（前回snapshotとの比較）
        if (previousPriceYen != null && item.getPriceYen() != null) {
            BigDecimal diff = item.getPriceYen().subtract(previousPriceYen).abs();
            BigDecimal changeRate = diff.divide(previousPriceYen, 4, java.math.RoundingMode.HALF_UP);
            double changePercent = changeRate.doubleValue() * 100;

            if (changePercent > VOLATILITY_THRESHOLD_HIGH) {
                score += PENALTY_HIGH_VOLATILITY;
                breakdown.add(breakdownEntry("HIGH_PRICE_VOLATILITY", PENALTY_HIGH_VOLATILITY,
                        String.format("価格変動 %.1f%%", changePercent)));
                riskFlags.add("HIGH_PRICE_VOLATILITY");
            } else if (changePercent > VOLATILITY_THRESHOLD_LOW) {
                score += PENALTY_VOLATILITY;
                breakdown.add(breakdownEntry("PRICE_VOLATILITY", PENALTY_VOLATILITY,
                        String.format("価格変動 %.1f%%", changePercent)));
                riskFlags.add("PRICE_VOLATILITY");
            }
        }

        // スコアを0-100にクランプ
        score = Math.max(0, Math.min(100, score));

        return new SafetyResult(score, breakdown, riskFlags);
    }

    /**
     * FreshnessScore計算
     * last_checked_atからの経過時間で算出
     */
    public int calculateFreshness(OffsetDateTime lastCheckedAt) {
        if (lastCheckedAt == null) {
            return 0;
        }

        long hours = Duration.between(lastCheckedAt, OffsetDateTime.now()).toHours();

        if (hours <= FRESHNESS_EXCELLENT_HOURS)
            return 100;
        if (hours <= FRESHNESS_GOOD_HOURS)
            return 80;
        if (hours <= FRESHNESS_FAIR_HOURS)
            return 50;
        if (hours <= FRESHNESS_STALE_HOURS)
            return 10;
        return 0;
    }

    /**
     * ProfitScore計算
     * PricingResultから算出。Gate FAILは0。
     */
    public int calculateProfit(BigDecimal profitRate, boolean gateProfitOk, boolean gateCashOk) {
        if (!gateProfitOk || !gateCashOk) {
            return 0;
        }
        if (profitRate == null) {
            return 0;
        }

        // 利益率をスコアに変換（20%=50点、40%=100点として線形補間）
        double rate = profitRate.doubleValue();
        if (rate <= 0)
            return 0;
        if (rate >= 0.40)
            return 100;

        // 0.20 -> 50, 0.40 -> 100 の線形補間
        // score = (rate - 0.20) / 0.20 * 50 + 50 when rate >= 0.20
        // score = rate / 0.20 * 50 when rate < 0.20
        if (rate >= 0.20) {
            return (int) Math.round((rate - 0.20) / 0.20 * 50 + 50);
        } else {
            return (int) Math.round(rate / 0.20 * 50);
        }
    }

    /**
     * OverallScore計算
     * profit 0.55 + safety 0.35 + freshness 0.10
     */
    public int calculateOverall(int profitScore, int safetyScore, int freshnessScore) {
        double overall = profitScore * WEIGHT_PROFIT + safetyScore * WEIGHT_SAFETY + freshnessScore * WEIGHT_FRESHNESS;
        return (int) Math.round(overall);
    }

    /**
     * アイテムの全スコアを再計算して更新
     */
    public void recalculateScores(DiscoveryItem item, BigDecimal previousPriceYen,
            BigDecimal profitRate, boolean gateProfitOk, boolean gateCashOk) {
        // Safety
        SafetyResult safety = calculateSafety(item, previousPriceYen);
        item.setSafetyScore(safety.score());
        item.setSafetyBreakdown(safety.breakdown());
        item.setRiskFlags(safety.riskFlags());

        // Freshness
        int freshnessScore = calculateFreshness(item.getLastCheckedAt());
        item.setFreshnessScore(freshnessScore);

        // Profit
        int profitScore = calculateProfit(profitRate, gateProfitOk, gateCashOk);
        item.setProfitScore(profitScore);

        // Overall
        int overallScore = calculateOverall(profitScore, safety.score(), freshnessScore);
        item.setOverallScore(overallScore);
    }

    private Map<String, Object> breakdownEntry(String reason, int delta, String description) {
        Map<String, Object> entry = new HashMap<>();
        entry.put("reason", reason);
        entry.put("delta", delta);
        entry.put("description", description);
        return entry;
    }

    // ----- Inner classes -----

    public record SafetyResult(
            int score,
            List<Map<String, Object>> breakdown,
            List<String> riskFlags) {
    }
}
