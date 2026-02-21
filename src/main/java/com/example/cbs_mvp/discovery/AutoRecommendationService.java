package com.example.cbs_mvp.discovery;

import com.example.cbs_mvp.dto.discovery.DiscoverySeed;
import com.example.cbs_mvp.fx.FxRateService;
import com.example.cbs_mvp.pricing.PricingCalculator;
import com.example.cbs_mvp.pricing.PricingRequest;
import com.example.cbs_mvp.pricing.PricingResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * 自動おすすめ商品取得サービス
 * Yahoo/Rakuten から価格帯で商品を一括取得し、
 * 利益ゲートを通過した商品のみを Discovery に登録する。
 */
@Service
public class AutoRecommendationService {

    private static final Logger log = LoggerFactory.getLogger(AutoRecommendationService.class);

    // === ユーザー設定値 ===
    private static final int SOURCE_PRICE_MIN = 2000; // 仕入単価下限
    private static final int SOURCE_PRICE_MAX = 12000; // 仕入単価上限
    private static final BigDecimal EBAY_SELL_MIN_USD = new BigDecimal("40"); // eBay売値下限
    private static final BigDecimal EBAY_SELL_MAX_USD = new BigDecimal("150"); // eBay売値上限
    private static final BigDecimal MIN_GROSS_PROFIT_YEN = new BigDecimal("2500"); // 最低粗利
    private static final BigDecimal MIN_ROI = new BigDecimal("0.25"); // ROI 25%
    private static final BigDecimal MIN_MARGIN = new BigDecimal("0.12"); // マージン 12%

    private static final BigDecimal DEFAULT_FX_RATE = new BigDecimal("150.0");

    private final List<ExternalItemSearchService> searchServices;
    private final DiscoveryIngestService ingestService;
    private final PricingCalculator pricingCalculator;
    private final FxRateService fxRateService;

    public AutoRecommendationService(
            List<ExternalItemSearchService> searchServices,
            DiscoveryIngestService ingestService,
            PricingCalculator pricingCalculator,
            FxRateService fxRateService) {
        this.searchServices = searchServices;
        this.ingestService = ingestService;
        this.pricingCalculator = pricingCalculator;
        this.fxRateService = fxRateService;
    }

    /**
     * 自動おすすめ取得を実行
     * 
     * @return 結果サマリ
     */
    public AutoRecommendResult execute() {
        BigDecimal fxRate = getCurrentFxRate();

        // 1. 全サービスから価格帯で商品を取得
        List<DiscoverySeed> allCandidates = new ArrayList<>();
        for (ExternalItemSearchService service : searchServices) {
            try {
                List<DiscoverySeed> results = service.searchByPriceRange(SOURCE_PRICE_MIN, SOURCE_PRICE_MAX);
                allCandidates.addAll(results);
                log.info("[AutoRecommend] {} returned {} items", service.getSourceType(), results.size());
            } catch (Exception e) {
                log.warn("[AutoRecommend] {} error: {}", service.getSourceType(), e.getMessage());
            }
        }

        log.info("[AutoRecommend] Total candidates fetched: {}", allCandidates.size());

        // 2. 利益ゲートでフィルタ＆登録
        int totalInserted = 0;
        int totalUpdated = 0;
        int skippedByGate = 0;

        for (DiscoverySeed seed : allCandidates) {
            try {
                // 利益ゲート判定
                if (!passesGate(seed, fxRate)) {
                    skippedByGate++;
                    continue;
                }

                boolean isNew = ingestService.upsert(seed);
                if (isNew) {
                    totalInserted++;
                } else {
                    totalUpdated++;
                }
            } catch (Exception e) {
                log.debug("[AutoRecommend] upsert error for {}: {}", seed.sourceUrl(), e.getMessage());
            }
        }

        log.info("[AutoRecommend] Done: fetched={}, inserted={}, updated={}, skipped={}",
                allCandidates.size(), totalInserted, totalUpdated, skippedByGate);

        return new AutoRecommendResult(allCandidates.size(), totalInserted, totalUpdated, skippedByGate);
    }

    /**
     * 利益ゲート判定:
     * 1. 粗利 ≥ ¥2,500
     * 2. ROI ≥ 25% (profitYen / totalCost)
     * 3. マージン ≥ 12% (profitYen / sellYen)
     * 4. 推定売価が $40〜$150 の範囲内
     */
    private boolean passesGate(DiscoverySeed seed, BigDecimal fxRate) {
        try {
            PricingRequest request = new PricingRequest();
            request.setSourcePriceYen(seed.priceYen());
            request.setWeightKg(seed.weightKg());
            request.setSizeTier(null);
            request.setFxRate(fxRate);
            request.setTargetSellUsd(null); // 自動算出

            PricingResponse res = pricingCalculator.calculate(request);

            BigDecimal profitYen = res.getProfitYen();
            BigDecimal totalCost = res.getTotalCostYen();
            BigDecimal sellYen = res.getSellYen();
            BigDecimal recSellUsd = res.getRecSellUsd();

            // 推定売価が範囲外ならスキップ
            if (recSellUsd.compareTo(EBAY_SELL_MIN_USD) < 0 || recSellUsd.compareTo(EBAY_SELL_MAX_USD) > 0) {
                return false;
            }

            // 粗利チェック
            if (profitYen.compareTo(MIN_GROSS_PROFIT_YEN) < 0) {
                return false;
            }

            // ROI チェック (profitYen / totalCost >= 0.25)
            if (totalCost.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal roi = profitYen.divide(totalCost, 6, RoundingMode.HALF_UP);
                if (roi.compareTo(MIN_ROI) < 0) {
                    return false;
                }
            }

            // マージンチェック (profitYen / sellYen >= 0.12)
            if (sellYen.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal margin = profitYen.divide(sellYen, 6, RoundingMode.HALF_UP);
                if (margin.compareTo(MIN_MARGIN) < 0) {
                    return false;
                }
            }

            return true;
        } catch (Exception e) {
            log.debug("[AutoRecommend] gate check error: {}", e.getMessage());
            return false;
        }
    }

    private BigDecimal getCurrentFxRate() {
        try {
            var fxResult = fxRateService.getCurrentRate();
            if (fxResult.isSuccess() && fxResult.rate() != null) {
                return fxResult.rate();
            }
        } catch (Exception e) {
            log.warn("[AutoRecommend] FX rate fetch failed, using default: {}", e.getMessage());
        }
        return DEFAULT_FX_RATE;
    }

    /**
     * 実行結果
     */
    public record AutoRecommendResult(
            int fetched,
            int inserted,
            int updated,
            int skipped) {
    }
}
