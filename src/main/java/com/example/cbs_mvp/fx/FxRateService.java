package com.example.cbs_mvp.fx;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.example.cbs_mvp.ops.SystemFlagService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FxRateService {

    private static final Logger log = LoggerFactory.getLogger(FxRateService.class);
    private static final String FX_RATE_FLAG = "FX_RATE";
    private static final String FX_RATE_UPDATED_AT_FLAG = "FX_RATE_UPDATED_AT";

    private final SystemFlagService flagService;
    private final WebClient.Builder webClientBuilder;

    @Value("${fx.api-key:}")
    private String apiKey;

    @Value("${fx.base-currency:USD}")
    private String baseCurrency;

    @Value("${fx.target-currency:JPY}")
    private String targetCurrency;

    /**
     * 毎日9時（日本時間）に為替レートを更新
     */
    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Tokyo")
    public void scheduledUpdate() {
        log.info("Scheduled FX rate update starting...");
        try {
            refreshRate();
        } catch (Exception e) {
            log.error("Scheduled FX rate update failed", e);
        }
    }

    /**
     * 為替レートを手動で更新
     */
    public FxRateResult refreshRate() {
        log.info("Refreshing FX rate {} -> {}", baseCurrency, targetCurrency);

        try {
            BigDecimal rate = fetchRateFromApi();

            if (rate == null || rate.compareTo(BigDecimal.ZERO) <= 0) {
                throw new RuntimeException("Invalid rate received: " + rate);
            }

            flagService.set(FX_RATE_FLAG, rate.setScale(4, RoundingMode.HALF_UP).toPlainString());
            flagService.set(FX_RATE_UPDATED_AT_FLAG, Instant.now().toString());

            log.info("FX rate updated: {} {} = {} {}",
                    "1", baseCurrency, rate, targetCurrency);

            return new FxRateResult(rate, Instant.now(), null);

        } catch (Exception e) {
            log.error("Failed to refresh FX rate", e);
            return new FxRateResult(null, null, e.getMessage());
        }
    }

    /**
     * 現在の為替レートを取得
     */
    public FxRateResult getCurrentRate() {
        String rateStr = flagService.get(FX_RATE_FLAG);
        String updatedAtStr = flagService.get(FX_RATE_UPDATED_AT_FLAG);

        if (rateStr == null || rateStr.isBlank()) {
            return new FxRateResult(null, null, "FX rate not set");
        }

        try {
            BigDecimal rate = new BigDecimal(rateStr);
            Instant updatedAt = updatedAtStr != null && !updatedAtStr.isBlank()
                    ? Instant.parse(updatedAtStr)
                    : null;
            return new FxRateResult(rate, updatedAt, null);
        } catch (Exception e) {
            return new FxRateResult(null, null, "Invalid stored rate: " + e.getMessage());
        }
    }

    private BigDecimal fetchRateFromApi() {
        // ExchangeRate-API (無料プラン対応)
        // https://www.exchangerate-api.com/

        if (apiKey == null || apiKey.isBlank()) {
            log.warn("FX API key not configured, using fallback rate");
            // フォールバック: 手動設定されたレートを使用
            String manualRate = flagService.get("FX_MANUAL_RATE");
            if (manualRate != null && !manualRate.isBlank()) {
                return new BigDecimal(manualRate);
            }
            // デフォルト値
            return new BigDecimal("150.00");
        }

        String url = String.format(
                "https://v6.exchangerate-api.com/v6/%s/pair/%s/%s",
                apiKey, baseCurrency, targetCurrency);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = webClientBuilder.build()
                    .get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response == null) {
                throw new RuntimeException("Empty response from FX API");
            }

            String result = (String) response.get("result");
            if (!"success".equals(result)) {
                throw new RuntimeException("FX API error: " + response.get("error-type"));
            }

            Object rateObj = response.get("conversion_rate");
            if (rateObj instanceof Number) {
                return BigDecimal.valueOf(((Number) rateObj).doubleValue());
            }

            throw new RuntimeException("Invalid rate format: " + rateObj);

        } catch (Exception e) {
            log.error("FX API call failed", e);
            throw new RuntimeException("FX API call failed: " + e.getMessage(), e);
        }
    }

    public record FxRateResult(BigDecimal rate, Instant updatedAt, String error) {
        public boolean isSuccess() {
            return error == null && rate != null;
        }
    }
}
