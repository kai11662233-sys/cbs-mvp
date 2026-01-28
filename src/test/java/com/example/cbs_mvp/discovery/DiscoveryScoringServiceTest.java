package com.example.cbs_mvp.discovery;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * DiscoveryScoringService のユニットテスト
 */
class DiscoveryScoringServiceTest {

    private final DiscoveryScoringService scoringService = new DiscoveryScoringService();

    // ===== Safety Score Tests =====

    @Test
    void safetySore_newCondition_officialSource_shouldGiveHighScore() {
        DiscoveryItem item = new DiscoveryItem();
        item.setCondition("NEW");
        item.setSourceType("OFFICIAL");
        item.setPriceYen(new BigDecimal("5000"));

        var result = scoringService.calculateSafety(item, null);

        // 100 + 10 (trusted) = 110 -> clamped to 100
        assertThat(result.score()).isEqualTo(100);
        assertThat(result.riskFlags()).isEmpty();
    }

    @Test
    void safetyScore_usedCondition_shouldDeduct20() {
        DiscoveryItem item = new DiscoveryItem();
        item.setCondition("USED");
        item.setSourceType("OTHER");
        item.setPriceYen(new BigDecimal("5000"));

        var result = scoringService.calculateSafety(item, null);

        // 100 - 20 = 80
        assertThat(result.score()).isEqualTo(80);
        assertThat(result.riskFlags()).contains("USED_CONDITION");
    }

    @Test
    void safetyScore_unknownCondition_shouldDeduct10() {
        DiscoveryItem item = new DiscoveryItem();
        item.setCondition("UNKNOWN");
        item.setSourceType("OTHER");
        item.setPriceYen(new BigDecimal("5000"));

        var result = scoringService.calculateSafety(item, null);

        // 100 - 10 = 90
        assertThat(result.score()).isEqualTo(90);
        assertThat(result.riskFlags()).contains("UNKNOWN_CONDITION");
    }

    @Test
    void safetyScore_c2cSource_shouldDeduct25() {
        DiscoveryItem item = new DiscoveryItem();
        item.setCondition("NEW");
        item.setSourceType("C2C");
        item.setPriceYen(new BigDecimal("5000"));

        var result = scoringService.calculateSafety(item, null);

        // 100 - 25 = 75
        assertThat(result.score()).isEqualTo(75);
        assertThat(result.riskFlags()).contains("C2C_SOURCE");
    }

    @Test
    void safetyScore_restrictedCategory_battery_shouldDeduct40AndFlagRestricted() {
        DiscoveryItem item = new DiscoveryItem();
        item.setCondition("NEW");
        item.setSourceType("OTHER");
        item.setCategoryHint("lithium battery pack");
        item.setPriceYen(new BigDecimal("5000"));

        var result = scoringService.calculateSafety(item, null);

        // 100 - 40 = 60
        assertThat(result.score()).isEqualTo(60);
        assertThat(result.riskFlags()).contains("RESTRICTED_CATEGORY");
    }

    @Test
    void safetyScore_restrictedCategory_cosmetic_shouldFlagRestricted() {
        DiscoveryItem item = new DiscoveryItem();
        item.setCondition("NEW");
        item.setSourceType("RETAIL");
        item.setCategoryHint("化粧品セット");
        item.setPriceYen(new BigDecimal("5000"));

        var result = scoringService.calculateSafety(item, null);

        // 100 + 10 (trusted) - 40 (restricted) = 70
        assertThat(result.score()).isEqualTo(70);
        assertThat(result.riskFlags()).contains("RESTRICTED_CATEGORY");
    }

    @Test
    void safetyScore_priceVolatility_over5percent_shouldDeduct10() {
        DiscoveryItem item = new DiscoveryItem();
        item.setCondition("NEW");
        item.setSourceType("OTHER");
        item.setPriceYen(new BigDecimal("5300")); // +6% from 5000

        var result = scoringService.calculateSafety(item, new BigDecimal("5000"));

        // 100 - 10 = 90
        assertThat(result.score()).isEqualTo(90);
        assertThat(result.riskFlags()).contains("PRICE_VOLATILITY");
    }

    @Test
    void safetyScore_priceVolatility_over10percent_shouldDeduct20() {
        DiscoveryItem item = new DiscoveryItem();
        item.setCondition("NEW");
        item.setSourceType("OTHER");
        item.setPriceYen(new BigDecimal("5600")); // +12% from 5000

        var result = scoringService.calculateSafety(item, new BigDecimal("5000"));

        // 100 - 20 = 80
        assertThat(result.score()).isEqualTo(80);
        assertThat(result.riskFlags()).contains("HIGH_PRICE_VOLATILITY");
    }

    @Test
    void safetyScore_multiplePenalties_shouldCombine() {
        DiscoveryItem item = new DiscoveryItem();
        item.setCondition("USED"); // -20
        item.setSourceType("C2C"); // -25
        item.setCategoryHint("battery"); // -40
        item.setPriceYen(new BigDecimal("5000"));

        var result = scoringService.calculateSafety(item, null);

        // 100 - 20 - 25 - 40 = 15
        assertThat(result.score()).isEqualTo(15);
        assertThat(result.riskFlags()).containsExactlyInAnyOrder(
                "USED_CONDITION", "C2C_SOURCE", "RESTRICTED_CATEGORY");
    }

    @Test
    void safetyScore_extremePenalties_shouldClampToZero() {
        DiscoveryItem item = new DiscoveryItem();
        item.setCondition("USED"); // -20
        item.setSourceType("C2C"); // -25
        item.setCategoryHint("medical"); // -40
        item.setPriceYen(new BigDecimal("6000")); // +20% volatility

        var result = scoringService.calculateSafety(item, new BigDecimal("5000"));

        // 100 - 20 - 25 - 40 - 20 = -5 -> clamped to 0
        assertThat(result.score()).isEqualTo(0);
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

    // ===== Profit Score Tests =====

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
    void profitScore_20percent_shouldReturn50() {
        int score = scoringService.calculateProfit(new BigDecimal("0.20"), true, true);
        assertThat(score).isEqualTo(50);
    }

    @Test
    void profitScore_40percent_shouldReturn100() {
        int score = scoringService.calculateProfit(new BigDecimal("0.40"), true, true);
        assertThat(score).isEqualTo(100);
    }

    @Test
    void profitScore_30percent_shouldReturn75() {
        int score = scoringService.calculateProfit(new BigDecimal("0.30"), true, true);
        assertThat(score).isEqualTo(75);
    }

    @Test
    void profitScore_10percent_shouldReturn25() {
        int score = scoringService.calculateProfit(new BigDecimal("0.10"), true, true);
        assertThat(score).isEqualTo(25);
    }

    // ===== Overall Score Tests =====

    @Test
    void overallScore_allMax_shouldReturn100() {
        int overall = scoringService.calculateOverall(100, 100, 100);
        assertThat(overall).isEqualTo(100);
    }

    @Test
    void overallScore_weighted_shouldApplyCorrectWeights() {
        // profit=100*0.55 + safety=100*0.35 + freshness=0*0.10 = 90
        int overall = scoringService.calculateOverall(100, 100, 0);
        assertThat(overall).isEqualTo(90);
    }

    @Test
    void overallScore_mixedValues_shouldCalculateCorrectly() {
        // profit=50*0.55 + safety=80*0.35 + freshness=50*0.10 = 27.5 + 28 + 5 = 60.5 ->
        // 61
        int overall = scoringService.calculateOverall(50, 80, 50);
        assertThat(overall).isEqualTo(61);
    }
}
