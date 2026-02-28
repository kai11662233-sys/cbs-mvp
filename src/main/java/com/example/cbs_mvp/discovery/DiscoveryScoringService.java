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
 *
 * === 変更履歴 ===
 * v2: 既存ペナルティ厳格化 + 重量/タイトル品質/ブランドリスク/価格帯の4項目追加
 */
@Service
public class DiscoveryScoringService {

    // ===== スコア減点・加点値（v2: 厳格化） =====
    private static final int PENALTY_USED_CONDITION = -30; // v1: -20
    private static final int PENALTY_UNKNOWN_CONDITION = -20; // v1: -10
    private static final int PENALTY_C2C_SOURCE = -35; // v1: -25
    private static final int PENALTY_RESTRICTED_CATEGORY = -40;
    private static final int PENALTY_HIGH_VOLATILITY = -30; // v1: -20
    private static final int PENALTY_VOLATILITY = -15; // v1: -10
    private static final int BONUS_TRUSTED_SOURCE = 10;

    // ===== 新規: 重量ペナルティ =====
    private static final int PENALTY_HEAVY_ITEM = -25; // 5kg超
    private static final int PENALTY_MEDIUM_WEIGHT = -10; // 3kg超
    private static final BigDecimal WEIGHT_HEAVY_KG = new BigDecimal("5.0");
    private static final BigDecimal WEIGHT_MEDIUM_KG = new BigDecimal("3.0");

    // ===== 新規: タイトル品質ペナルティ =====
    private static final int PENALTY_TITLE_TOO_SHORT = -15; // 10文字未満
    private static final int PENALTY_TITLE_TOO_LONG = -10; // 200文字超
    private static final int TITLE_MIN_LENGTH = 10;
    private static final int TITLE_MAX_LENGTH = 200;

    // ===== 新規: ブランドリスク（VeRO対象ブランド） =====
    private static final int PENALTY_VERO_BRAND = -35;
    private static final Set<String> VERO_RISK_BRANDS = Set.of(
            // 主要VeRO登録ブランド（eBayで出品制限リスクが高い）
            "nike", "adidas", "puma", "new balance", "converse",
            "gucci", "louis vuitton", "chanel", "hermes", "prada",
            "rolex", "omega", "cartier", "breitling",
            "disney", "sanrio", "pokemon", "nintendo", "sony",
            "apple", "dyson", "bose",
            "supreme", "off-white", "balenciaga",
            "ナイキ", "アディダス", "プーマ", "ニューバランス", "コンバース",
            "グッチ", "ルイヴィトン", "シャネル", "エルメス", "プラダ",
            "ロレックス", "オメガ", "カルティエ",
            "ディズニー", "サンリオ", "ポケモン", "任天堂", "ソニー",
            "アップル", "ダイソン", "ボーズ");

    // ===== 新規: 価格帯スイートスポット =====
    private static final int PENALTY_PRICE_OUT_OF_SWEET = -10;
    private static final BigDecimal SWEET_MIN_YEN = new BigDecimal("3000");
    private static final BigDecimal SWEET_MAX_YEN = new BigDecimal("10000");

    // ===== 閾値 =====
    private static final double VOLATILITY_THRESHOLD_HIGH = 10.0;
    private static final double VOLATILITY_THRESHOLD_LOW = 5.0;

    // ===== Overall重み付け（v2: 利益重視） =====
    private static final double WEIGHT_PROFIT = 0.60; // v1: 0.55
    private static final double WEIGHT_SAFETY = 0.30; // v1: 0.35
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

        // 5. [NEW] 重量ペナルティ（国際送料への影響）
        BigDecimal weight = item.getWeightKg();
        if (weight != null) {
            if (weight.compareTo(WEIGHT_HEAVY_KG) > 0) {
                score += PENALTY_HEAVY_ITEM;
                breakdown.add(breakdownEntry("HEAVY_ITEM", PENALTY_HEAVY_ITEM,
                        String.format("重量 %.1fkg (5kg超: 国際送料高額)", weight.doubleValue())));
                riskFlags.add("HEAVY_ITEM");
            } else if (weight.compareTo(WEIGHT_MEDIUM_KG) > 0) {
                score += PENALTY_MEDIUM_WEIGHT;
                breakdown.add(breakdownEntry("MEDIUM_WEIGHT", PENALTY_MEDIUM_WEIGHT,
                        String.format("重量 %.1fkg (3kg超: 送料注意)", weight.doubleValue())));
                riskFlags.add("MEDIUM_WEIGHT");
            }
        }

        // 6. [NEW] タイトル品質チェック
        String title = item.getTitle();
        if (title != null) {
            int titleLen = title.length();
            if (titleLen < TITLE_MIN_LENGTH) {
                score += PENALTY_TITLE_TOO_SHORT;
                breakdown.add(breakdownEntry("TITLE_TOO_SHORT", PENALTY_TITLE_TOO_SHORT,
                        String.format("タイトル短すぎ (%d文字 < %d文字)", titleLen, TITLE_MIN_LENGTH)));
                riskFlags.add("TITLE_TOO_SHORT");
            } else if (titleLen > TITLE_MAX_LENGTH) {
                score += PENALTY_TITLE_TOO_LONG;
                breakdown.add(breakdownEntry("TITLE_TOO_LONG", PENALTY_TITLE_TOO_LONG,
                        String.format("タイトル長すぎ (%d文字 > %d文字)", titleLen, TITLE_MAX_LENGTH)));
                riskFlags.add("TITLE_TOO_LONG");
            }
        } else {
            score += PENALTY_TITLE_TOO_SHORT;
            breakdown.add(breakdownEntry("TITLE_MISSING", PENALTY_TITLE_TOO_SHORT, "タイトル未設定"));
            riskFlags.add("TITLE_MISSING");
        }

        // 7. [NEW] ブランドリスク（VeRO対象ブランド検出）
        if (title != null && !title.isBlank()) {
            String lowerTitle = title.toLowerCase();
            for (String brand : VERO_RISK_BRANDS) {
                if (lowerTitle.contains(brand)) {
                    score += PENALTY_VERO_BRAND;
                    breakdown.add(breakdownEntry("VERO_BRAND_RISK", PENALTY_VERO_BRAND,
                            "VeRO対象ブランド検出: " + brand));
                    riskFlags.add("VERO_BRAND:" + brand);
                    break; // 1ブランドのみカウント
                }
            }
        }

        // 8. [NEW] 価格帯スイートスポット判定
        BigDecimal price = item.getPriceYen();
        if (price != null) {
            if (price.compareTo(SWEET_MIN_YEN) < 0 || price.compareTo(SWEET_MAX_YEN) > 0) {
                score += PENALTY_PRICE_OUT_OF_SWEET;
                breakdown.add(breakdownEntry("PRICE_OUT_OF_SWEET_SPOT", PENALTY_PRICE_OUT_OF_SWEET,
                        String.format("価格帯 ¥%s (推奨: ¥%s〜¥%s)", price, SWEET_MIN_YEN, SWEET_MAX_YEN)));
                riskFlags.add("PRICE_OUT_OF_SWEET_SPOT");
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
     * ProfitScore計算（v2: 基準引き上げ）
     * PricingResultから算出。Gate FAILは0。
     *
     * v2: 25%=50点、50%=100点（v1: 20%=50点、40%=100点）
     */
    public int calculateProfit(BigDecimal profitRate, boolean gateProfitOk, boolean gateCashOk) {
        if (!gateProfitOk || !gateCashOk) {
            return 0;
        }
        if (profitRate == null) {
            return 0;
        }

        double rate = profitRate.doubleValue();
        if (rate <= 0)
            return 0;
        if (rate >= 0.50)
            return 100;

        // 0.25 -> 50, 0.50 -> 100 の線形補間
        if (rate >= 0.25) {
            return (int) Math.round((rate - 0.25) / 0.25 * 50 + 50);
        } else {
            // 0 -> 0, 0.25 -> 50 の線形補間
            return (int) Math.round(rate / 0.25 * 50);
        }
    }

    /**
     * OverallScore計算
     * profit 0.60 + safety 0.30 + freshness 0.10
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
