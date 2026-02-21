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
    private final DiscoveryItemValidator validator;
    private final PricingCalculator pricingCalculator;
    private final FxRateService fxRateService;
    private final StateTransitionService transitions;

    public DiscoveryIngestService(
            DiscoveryItemRepository repository,
            DiscoveryScoringService scoringService,
            DiscoveryItemValidator validator,
            PricingCalculator pricingCalculator,
            FxRateService fxRateService,
            StateTransitionService transitions) {
        this.repository = repository;
        this.scoringService = scoringService;
        this.validator = validator;
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
        int rowNum = 1;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
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

                    // バリデーション
                    var validation = validator.validate(seed);
                    if (!validation.ok()) {
                        errors.add(new CsvIngestError(rowNum,
                                String.join("; ", validation.errors()), rawLine));
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
        // --- URL正規化 ---
        String normalizedUrl = validator.normalizeUrl(seed.sourceUrl());

        // --- URL完全一致での重複チェック ---
        Optional<DiscoveryItem> existingOpt = repository.findBySourceUrl(normalizedUrl);

        // --- URL不一致の場合、タイトル+価格帯で疑似重複判定 ---
        if (existingOpt.isEmpty() && seed.title() != null && !seed.title().isBlank()
                && seed.priceYen() != null) {
            BigDecimal priceLow = seed.priceYen().multiply(new BigDecimal("0.80"));
            BigDecimal priceHigh = seed.priceYen().multiply(new BigDecimal("1.20"));
            var titleMatches = repository.findByTitleAndPriceRange(
                    seed.title(), priceLow, priceHigh);
            if (!titleMatches.isEmpty()) {
                existingOpt = Optional.of(titleMatches.get(0));
                log.info("タイトル+価格帯で疑似重複を検出: title='{}' → 既存ID={}",
                        seed.title(), titleMatches.get(0).getId());
            }
        }

        DiscoveryItem item;
        boolean isNew;
        BigDecimal previousPriceYen = null;

        if (existingOpt.isPresent()) {
            item = existingOpt.get();
            previousPriceYen = item.getPriceYen();
            isNew = false;
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
            item = new DiscoveryItem();
            item.setSourceUrl(normalizedUrl);
            item.setSourceDomain(extractDomain(normalizedUrl));
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

        // --- NGキーワードチェック ---
        if (validator.containsNgKeyword(item.getTitle())) {
            List<String> ngWords = validator.findNgKeywords(item.getTitle());
            List<String> flags = item.getRiskFlags() != null
                    ? new ArrayList<>(item.getRiskFlags())
                    : new ArrayList<>();
            for (String ng : ngWords) {
                String flag = "NG_KEYWORD:" + ng;
                if (!flags.contains(flag))
                    flags.add(flag);
            }
            item.setRiskFlags(flags);
            log.info("NGキーワード検出: title='{}' keywords={}", item.getTitle(), ngWords);
        }

        // ProfitScore概算計算
        ProfitEstimate estimate = calculateProfitEstimate(item);

        scoringService.recalculateScores(
                item,
                previousPriceYen,
                estimate.profitRate(),
                estimate.gateProfitOk(),
                true);

        // ステータス更新
        if (item.hasRestrictedCategory()) {
            item.setStatus("NG");
        } else if (validator.containsNgKeyword(item.getTitle())) {
            item.setStatus("NG");
        } else if (item.getSafetyScore() < 50) {
            item.setStatus("NG");
        } else {
            item.setStatus("CHECKED");
        }

        item = repository.save(item);

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
