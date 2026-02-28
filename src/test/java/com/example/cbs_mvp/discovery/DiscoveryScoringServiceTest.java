package com.example.cbs_mvp.discovery;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * DiscoveryScoringService のユニットテスト
 * v2: 厳格化されたペナルティ + 新規4項目に対応
 */
class DiscoveryScoringServiceTest {

    private final DiscoveryScoringService scoringService = new DiscoveryScoringService();

    // ===== Safety Score Tests =====

    @Test
    void safetySore_newCondition_officialSource_shouldGiveHighScore() {
        DiscoveryItem item = new DiscoveryItem();
        item.setCondition("NEW");
        item.setSourceType("OFFICIAL");
        item.setTitle("テスト商品名 サンプル品 高品質"); // 10文字以上
        item.setPriceYen(new BigDecimal("5000")); // スイートスポット内

        var result = scoringService.calculateSafety(item, null);

        // 100 + 10 (trusted) = 110 -> clamped to 100
        assertThat(result.score()).isEqualTo(100);
        assertThat(result.riskFlags()).isEmpty();
    }

    @Test
    void safetyScore_usedCondition_shouldDeduct30() {
        DiscoveryItem item = new DiscoveryItem();
        item.setCondition("USED");
        item.setSourceType("OTHER");
        item.setTitle("テスト商品名 中古品 状態良好です");
        item.setPriceYen(new BigDecimal("5000"));

        var result = scoringService.calculateSafety(item, null);

        // 100 - 30 (USED) = 70
        assertThat(result.score()).isEqualTo(70);
        assertThat(result.riskFlags()).contains("USED_CONDITION");
    }

    @Test
    void safetyScore_unknownCondition_shouldDeduct20() {
        DiscoveryItem item = new DiscoveryItem();
        item.setCondition("UNKNOWN");
        item.setSourceType("OTHER");
        item.setTitle("テスト商品名 状態不明のアイテム");
        item.setPriceYen(new BigDecimal("5000"));

        var result = scoringService.calculateSafety(item, null);

        // 100 - 20 (UNKNOWN) = 80
        assertThat(result.score()).isEqualTo(80);
        assertThat(result.riskFlags()).contains("UNKNOWN_CONDITION");
    }

    @Test
    void safetyScore_c2cSource_shouldDeduct35() {
        DiscoveryItem item = new DiscoveryItem();
        item.setCondition("NEW");
        item.setSourceType("C2C");
        item.setTitle("テスト商品名 メルカリ出品のアイテム");
        item.setPriceYen(new BigDecimal("5000"));

        var result = scoringService.calculateSafety(item, null);

        // 100 - 35 (C2C) = 65
        assertThat(result.score()).isEqualTo(65);
        assertThat(result.riskFlags()).contains("C2C_SOURCE");
    }

    @Test
    void safetyScore_restrictedCategory_battery_shouldDeduct40AndFlagRestricted() {
        DiscoveryItem item = new DiscoveryItem();
        item.setCondition("NEW");
        item.setSourceType("OTHER");
        item.setCategoryHint("lithium battery pack");
        item.setTitle("テスト商品名 リチウムバッテリーパック大容量");
        item.setPriceYen(new BigDecimal("5000"));

        var result = scoringService.calculateSafety(item, null);

        // 100 - 40 (restricted) = 60
        assertThat(result.score()).isEqualTo(60);
        assertThat(result.riskFlags()).contains("RESTRICTED_CATEGORY");
    }

    @Test
    void safetyScore_restrictedCategory_cosmetic_shouldFlagRestricted() {
        DiscoveryItem item = new DiscoveryItem();
        item.setCondition("NEW");
        item.setSourceType("RETAIL");
        item.setCategoryHint("化粧品セット");
        item.setTitle("テスト商品名 化粧品セット お得パック");
        item.setPriceYen(new BigDecimal("5000"));

        var result = scoringService.calculateSafety(item, null);

        // 100 + 10 (trusted) - 40 (restricted) = 70
        assertThat(result.score()).isEqualTo(70);
        assertThat(result.riskFlags()).contains("RESTRICTED_CATEGORY");
    }

    @Test
    void safetyScore_priceVolatility_over5percent_shouldDeduct15() {
        DiscoveryItem item = new DiscoveryItem();
        item.setCondition("NEW");
        item.setSourceType("OTHER");
        item.setTitle("テスト商品名 価格変動ありのアイテム");
        item.setPriceYen(new BigDecimal("5300")); // +6% from 5000

        var result = scoringService.calculateSafety(item, new BigDecimal("5000"));

        // 100 - 15 (volatility) = 85
        assertThat(result.score()).isEqualTo(85);
        assertThat(result.riskFlags()).contains("PRICE_VOLATILITY");
    }

    @Test
    void safetyScore_priceVolatility_over10percent_shouldDeduct30() {
        DiscoveryItem item = new DiscoveryItem();
        item.setCondition("NEW");
        item.setSourceType("OTHER");
        item.setTitle("テスト商品名 大幅な価格変動のアイテム");
        item.setPriceYen(new BigDecimal("5600")); // +12% from 5000

        var result = scoringService.calculateSafety(item, new BigDecimal("5000"));

        // 100 - 30 (high volatility) = 70
        assertThat(result.score()).isEqualTo(70);
        assertThat(result.riskFlags()).contains("HIGH_PRICE_VOLATILITY");
    }

    @Test
    void safetyScore_multiplePenalties_shouldCombine() {
        DiscoveryItem item = new DiscoveryItem();
        item.setCondition("USED"); // -30
        item.setSourceType("C2C"); // -35
        item.setCategoryHint("battery"); // -40
        item.setTitle("テスト商品名 中古品バッテリー出品");
        item.setPriceYen(new BigDecimal("5000"));

        var result = scoringService.calculateSafety(item, null);

        // 100 - 30 - 35 - 40 = -5 -> clamped to 0
        assertThat(result.score()).isEqualTo(0);
        assertThat(result.riskFlags()).containsExactlyInAnyOrder(
                "USED_CONDITION", "C2C_SOURCE", "RESTRICTED_CATEGORY");
    }

    @Test
    void safetyScore_extremePenalties_shouldClampToZero() {
        DiscoveryItem item = new DiscoveryItem();
        item.setCondition("USED"); // -30
        item.setSourceType("C2C"); // -35
        item.setCategoryHint("medical"); // -40
        item.setTitle("テスト商品名 中古医療機器 C2C出品");
        item.setPriceYen(new BigDecimal("6000")); // +20% volatility

        var result = scoringService.calculateSafety(item, new BigDecimal("5000"));

        // 100 - 30 - 35 - 40 - 30 = -35 -> clamped to 0
        assertThat(result.score()).isEqualTo(0);
    }

    // ===== NEW: Weight Penalty Tests =====

    @Test
    void safetyScore_heavyItem_shouldDeduct25() {
        DiscoveryItem item = new DiscoveryItem();
        item.setCondition("NEW");
        item.setSourceType("RETAIL");
        item.setTitle("テスト商品名 大型家電製品 重量あり");
        item.setPriceYen(new BigDecimal("8000"));
        item.setWeightKg(new BigDecimal("6.0")); // 5kg超

        var result = scoringService.calculateSafety(item, null);

        // 100 + 10 (trusted) - 25 (heavy) = 85
        assertThat(result.score()).isEqualTo(85);
        assertThat(result.riskFlags()).contains("HEAVY_ITEM");
    }

    @Test
    void safetyScore_mediumWeight_shouldDeduct10() {
        DiscoveryItem item = new DiscoveryItem();
        item.setCondition("NEW");
        item.setSourceType("RETAIL");
        item.setTitle("テスト商品名 やや重めの商品です");
        item.setPriceYen(new BigDecimal("5000"));
        item.setWeightKg(new BigDecimal("4.0")); // 3-5kg

        var result = scoringService.calculateSafety(item, null);

        // 100 + 10 (trusted) - 10 (medium weight) = 100 (clamped)
        assertThat(result.score()).isEqualTo(100);
        assertThat(result.riskFlags()).contains("MEDIUM_WEIGHT");
    }

    // ===== NEW: Title Quality Tests =====

    @Test
    void safetyScore_titleTooShort_shouldDeduct15() {
        DiscoveryItem item = new DiscoveryItem();
        item.setCondition("NEW");
        item.setSourceType("OTHER");
        item.setTitle("短いタイトル"); // < 10文字
        item.setPriceYen(new BigDecimal("5000"));

        var result = scoringService.calculateSafety(item, null);

        assertThat(result.riskFlags()).contains("TITLE_TOO_SHORT");
    }

    @Test
    void safetyScore_titleMissing_shouldDeduct15() {
        DiscoveryItem item = new DiscoveryItem();
        item.setCondition("NEW");
        item.setSourceType("OTHER");
        item.setPriceYen(new BigDecimal("5000"));
        // title未設定

        var result = scoringService.calculateSafety(item, null);

        assertThat(result.riskFlags()).contains("TITLE_MISSING");
    }

    // ===== NEW: VeRO Brand Risk Tests =====

    @Test
    void safetyScore_veroBrand_nike_shouldDeduct35() {
        DiscoveryItem item = new DiscoveryItem();
        item.setCondition("NEW");
        item.setSourceType("RETAIL");
        item.setTitle("Nike Air Max 90 メンズスニーカー 限定カラー");
        item.setPriceYen(new BigDecimal("8000"));

        var result = scoringService.calculateSafety(item, null);

        // 100 + 10 (trusted) - 35 (VeRO) = 75
        assertThat(result.score()).isEqualTo(75);
        assertThat(result.riskFlags().stream().anyMatch(f -> f.startsWith("VERO_BRAND:"))).isTrue();
    }

    @Test
    void safetyScore_veroBrand_japanese_shouldDetect() {
        DiscoveryItem item = new DiscoveryItem();
        item.setCondition("NEW");
        item.setSourceType("OTHER");
        item.setTitle("ディズニー ミッキーマウス フィギュア 限定品");
        item.setPriceYen(new BigDecimal("5000"));

        var result = scoringService.calculateSafety(item, null);

        assertThat(result.riskFlags().stream().anyMatch(f -> f.startsWith("VERO_BRAND:"))).isTrue();
    }

    // ===== NEW: Price Sweet Spot Tests =====

    @Test
    void safetyScore_priceOutOfSweetSpot_low_shouldDeduct10() {
        DiscoveryItem item = new DiscoveryItem();
        item.setCondition("NEW");
        item.setSourceType("OTHER");
        item.setTitle("テスト商品名 低価格帯のアイテムです");
        item.setPriceYen(new BigDecimal("2000")); // <3000

        var result = scoringService.calculateSafety(item, null);

        assertThat(result.riskFlags()).contains("PRICE_OUT_OF_SWEET_SPOT");
    }

    @Test
    void safetyScore_priceOutOfSweetSpot_high_shouldDeduct10() {
        DiscoveryItem item = new DiscoveryItem();
        item.setCondition("NEW");
        item.setSourceType("OTHER");
        item.setTitle("テスト商品名 高価格帯のアイテムです");
        item.setPriceYen(new BigDecimal("15000")); // >10000

        var result = scoringService.calculateSafety(item, null);

        assertThat(result.riskFlags()).contains("PRICE_OUT_OF_SWEET_SPOT");
    }

    // ===== Freshness Score Tests =====

    @Test
    void freshnessScore_withinOneHour_shouldReturn100() {
        OffsetDateTime recentCheck = OffsetDateTime.now().minusMinutes(30);
        int score = scoringService.calculateFreshness(recentCheck);
        assertThat(score).isEqualTo(100);
    }

    @Test
    void freshnessScore_within6Hours_shouldReturn80() {
        OffsetDateTime check = OffsetDateTime.now().minusHours(3);
        int score = scoringService.calculateFreshness(check);
        assertThat(score).isEqualTo(80);
    }

    @Test
    void freshnessScore_within24Hours_shouldReturn50() {
        OffsetDateTime check = OffsetDateTime.now().minusHours(12);
        int score = scoringService.calculateFreshness(check);
        assertThat(score).isEqualTo(50);
    }

    @Test
    void freshnessScore_within72Hours_shouldReturn10() {
        OffsetDateTime check = OffsetDateTime.now().minusHours(48);
        int score = scoringService.calculateFreshness(check);
        assertThat(score).isEqualTo(10);
    }

    @Test
    void freshnessScore_over72Hours_shouldReturn0() {
        OffsetDateTime check = OffsetDateTime.now().minusDays(5);
        int score = scoringService.calculateFreshness(check);
        assertThat(score).isEqualTo(0);
    }

    @Test
    void freshnessScore_null_shouldReturn0() {
        int score = scoringService.calculateFreshness(null);
        assertThat(score).isEqualTo(0);
    }

    // ===== Profit Score Tests (v2: 基準引き上げ) =====

    @Test
    void profitScore_gatesFail_shouldReturn0() {
        int score = scoringService.calculateProfit(new BigDecimal("0.25"), false, true);
        assertThat(score).isEqualTo(0);
    }

    @Test
    void profitScore_cashGateFails_shouldReturn0() {
        int score = scoringService.calculateProfit(new BigDecimal("0.25"), true, false);
        assertThat(score).isEqualTo(0);
    }

    @Test
    void profitScore_25percent_shouldReturn50() {
        // v2: 25%=50点 (v1: 20%=50点)
        int score = scoringService.calculateProfit(new BigDecimal("0.25"), true, true);
        assertThat(score).isEqualTo(50);
    }

    @Test
    void profitScore_50percent_shouldReturn100() {
        // v2: 50%=100点 (v1: 40%=100点)
        int score = scoringService.calculateProfit(new BigDecimal("0.50"), true, true);
        assertThat(score).isEqualTo(100);
    }

    @Test
    void profitScore_375percent_shouldReturn75() {
        // v2: 37.5% = 75点 (0.375 - 0.25) / 0.25 * 50 + 50 = 75
        int score = scoringService.calculateProfit(new BigDecimal("0.375"), true, true);
        assertThat(score).isEqualTo(75);
    }

    @Test
    void profitScore_125percent_shouldReturn25() {
        // v2: 12.5% = 25点 (0.125 / 0.25 * 50 = 25)
        int score = scoringService.calculateProfit(new BigDecimal("0.125"), true, true);
        assertThat(score).isEqualTo(25);
    }

    // ===== Overall Score Tests (v2: 重み変更) =====

    @Test
    void overallScore_allMax_shouldReturn100() {
        int overall = scoringService.calculateOverall(100, 100, 100);
        assertThat(overall).isEqualTo(100);
    }

    @Test
    void overallScore_weighted_shouldApplyCorrectWeights() {
        // v2: profit=100*0.60 + safety=100*0.30 + freshness=0*0.10 = 90
        int overall = scoringService.calculateOverall(100, 100, 0);
        assertThat(overall).isEqualTo(90);
    }

    @Test
    void overallScore_mixedValues_shouldCalculateCorrectly() {
        // v2: profit=50*0.60 + safety=80*0.30 + freshness=50*0.10 = 30 + 24 + 5 = 59
        int overall = scoringService.calculateOverall(50, 80, 50);
        assertThat(overall).isEqualTo(59);
    }
}
