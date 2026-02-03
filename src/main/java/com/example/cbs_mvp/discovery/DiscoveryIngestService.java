package com.example.cbs_mvp.discovery;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.cbs_mvp.dto.discovery.CsvIngestError;
import com.example.cbs_mvp.dto.discovery.CsvIngestResultResponse;
import com.example.cbs_mvp.dto.discovery.DiscoverySeed;
import com.example.cbs_mvp.fx.FxRateService;
import com.example.cbs_mvp.pricing.PricingCalculator;
import com.example.cbs_mvp.pricing.PricingRequest;
import com.example.cbs_mvp.pricing.PricingResponse;
import com.example.cbs_mvp.service.StateTransitionService;

/**
 * Discovery取り込みサービス
 * - CSVパースと一括登録
 * - ProfitScore概算計算
 */
@Service
public class DiscoveryIngestService {

    private static final Logger log = LoggerFactory.getLogger(DiscoveryIngestService.class);
    private static final BigDecimal DEFAULT_FX_RATE = new BigDecimal("150.0");

    private final DiscoveryItemRepository repository;
    private final DiscoveryScoringService scoringService;
    private final PricingCalculator pricingCalculator;
    private final FxRateService fxRateService;
    private final StateTransitionService transitions;

    public DiscoveryIngestService(
            DiscoveryItemRepository repository,
            DiscoveryScoringService scoringService,
            PricingCalculator pricingCalculator,
            FxRateService fxRateService,
            StateTransitionService transitions) {
        this.repository = repository;
        this.scoringService = scoringService;
        this.pricingCalculator = pricingCalculator;
        this.fxRateService = fxRateService;
        this.transitions = transitions;
    }

    /**
     * CSVファイルを読み込んでDiscoveryItemsを登録/更新
     */
    @Transactional
    public CsvIngestResultResponse ingestFromCsv(InputStream inputStream) throws IOException {
        List<CsvIngestError> errors = new ArrayList<>();
        int inserted = 0;
        int updated = 0;
        int rowNum = 1; // ヘッダ行を1として開始

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            // ヘッダ行を読み飛ばす
            String headerLine = reader.readLine();
            if (headerLine == null) {
                errors.add(new CsvIngestError(1, "Empty file", ""));
                return new CsvIngestResultResponse(0, 0, errors);
            }

            String line;
            while ((line = reader.readLine()) != null) {
                rowNum++;
                String rawLine = line;

                try {
                    DiscoverySeed seed = parseCsvLine(line);
                    if (seed.sourceUrl() == null || seed.sourceUrl().isBlank()) {
                        errors.add(new CsvIngestError(rowNum, "source_url is required", rawLine));
                        continue;
                    }
                    if (seed.priceYen() == null) {
                        errors.add(new CsvIngestError(rowNum, "price_yen is required", rawLine));
                        continue;
                    }

                    boolean isNew = upsert(seed);
                    if (isNew) {
                        inserted++;
                    } else {
                        updated++;
                    }
                } catch (Exception e) {
                    errors.add(new CsvIngestError(rowNum, e.getMessage(), rawLine));
                }
            }
        }

        log.info("CSV ingest complete: inserted={}, updated={}, errors={}", inserted, updated, errors.size());
        return new CsvIngestResultResponse(inserted, updated, errors);
    }

    /**
     * 単一のSeedをupsert
     * 
     * @return true if inserted (new), false if updated (existing)
     */
    public boolean upsert(DiscoverySeed seed) {
        Optional<DiscoveryItem> existingOpt = repository.findBySourceUrl(seed.sourceUrl());

        DiscoveryItem item;
        boolean isNew;
        BigDecimal previousPriceYen = null;

        if (existingOpt.isPresent()) {
            // 既存: 更新
            item = existingOpt.get();
            previousPriceYen = item.getPriceYen();
            isNew = false;
            // フィールド更新
            if (seed.title() != null && !seed.title().isBlank())
                item.setTitle(seed.title());
            if (seed.condition() != null && !seed.condition().isBlank())
                item.setCondition(seed.condition());
            if (seed.sourceType() != null && !seed.sourceType().isBlank())
                item.setSourceType(seed.sourceType());
            if (seed.categoryHint() != null)
                item.setCategoryHint(seed.categoryHint());
            if (seed.priceYen() != null)
                item.setPriceYen(seed.priceYen());
            if (seed.shippingYen() != null)
                item.setShippingYen(seed.shippingYen());
            if (seed.weightKg() != null)
                item.setWeightKg(seed.weightKg());
            if (seed.notes() != null)
                item.setNotes(seed.notes());
        } else {
            // 新規: 作成
            item = new DiscoveryItem();
            item.setSourceUrl(seed.sourceUrl());
            item.setSourceDomain(extractDomain(seed.sourceUrl()));
            item.setSourceType(seed.sourceType() != null ? seed.sourceType() : "OTHER");
            item.setTitle(seed.title());
            item.setCondition(seed.condition() != null ? seed.condition() : "UNKNOWN");
            item.setCategoryHint(seed.categoryHint());
            item.setPriceYen(seed.priceYen());
            item.setShippingYen(seed.shippingYen());
            item.setWeightKg(seed.weightKg());
            item.setNotes(seed.notes());
            item.setStatus("NEW");
            isNew = true;
        }

        // 共通: lastCheckedAt更新、スナップショット更新
        item.setLastCheckedAt(OffsetDateTime.now());

        Map<String, Object> snapshot = item.getSnapshot();
        if (snapshot == null) {
            snapshot = new HashMap<>();
        }
        if (isNew) {
            snapshot.put("initialPriceYen", seed.priceYen());
            snapshot.put("registeredAt", OffsetDateTime.now().toString());
        } else {
            snapshot.put("lastPriceYen", seed.priceYen());
            snapshot.put("lastCheckedAt", OffsetDateTime.now().toString());
            snapshot.put("previousPriceYen", previousPriceYen);
        }
        item.setSnapshot(snapshot);

        // ProfitScore概算計算
        ProfitEstimate estimate = calculateProfitEstimate(item);

        // スコア再計算
        scoringService.recalculateScores(
                item,
                previousPriceYen,
                estimate.profitRate(),
                estimate.gateProfitOk(),
                true // gateCashOk
        );

        // ステータス更新
        if (item.hasRestrictedCategory()) {
            item.setStatus("NG");
        } else if (item.getSafetyScore() < 50) {
            item.setStatus("NG");
        } else {
            item.setStatus("CHECKED");
        }

        item = repository.save(item);

        // 監査ログ（新規の場合のみ記録）
        if (isNew) {
            transitions.log("DISCOVERY_ITEM", item.getId(), null, "NEW", null, "CSV Ingest", "SYSTEM", cid());
        }

        return isNew;
    }

    private ProfitEstimate calculateProfitEstimate(DiscoveryItem item) {
        try {
            BigDecimal fxRate = DEFAULT_FX_RATE;
            var fxResult = fxRateService.getCurrentRate();
            if (fxResult.isSuccess() && fxResult.rate() != null) {
                fxRate = fxResult.rate();
            }

            PricingRequest request = new PricingRequest();
            request.setSourcePriceYen(item.getPriceYen());
            request.setWeightKg(item.getWeightKg());
            request.setSizeTier(null);
            request.setFxRate(fxRate);
            request.setTargetSellUsd(null);

            PricingResponse response = pricingCalculator.calculate(request);

            return new ProfitEstimate(
                    response.getProfitRate(),
                    response.isGateProfitOk());
        } catch (Exception e) {
            log.warn("Failed to calculate profit estimate for item: {}", item.getSourceUrl(), e);
            return new ProfitEstimate(BigDecimal.ZERO, false);
        }
    }

    private DiscoverySeed parseCsvLine(String line) {
        String[] cols = line.split(",", -1);
        if (cols.length < 5) {
            throw new IllegalArgumentException("Invalid CSV format: expected at least 5 columns");
        }

        return new DiscoverySeed(
                cols[0].trim(), // sourceUrl
                cols.length > 1 ? cols[1].trim() : null, // title
                cols.length > 4 ? cols[4].trim() : null, // condition
                null, // sourceType
                null, // categoryHint
                cols.length > 2 ? parseDecimal(cols[2]) : null, // priceYen
                null, // shippingYen
                cols.length > 3 ? parseDecimal(cols[3]) : null, // weightKg
                null); // notes
    }

    private BigDecimal parseDecimal(String s) {
        if (s == null || s.trim().isEmpty()) {
            return null;
        }
        return new BigDecimal(s.trim());
    }

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

    private String cid() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private record ProfitEstimate(BigDecimal profitRate, boolean gateProfitOk) {
    }
}
