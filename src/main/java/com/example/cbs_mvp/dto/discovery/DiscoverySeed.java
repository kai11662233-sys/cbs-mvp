package com.example.cbs_mvp.dto.discovery;

import java.math.BigDecimal;

/**
 * CSV取り込み用の中間データレコード
 */
public record DiscoverySeed(
        String sourceUrl,
        String title,
        String condition,
        String sourceType,
        String categoryHint,
        BigDecimal priceYen,
        BigDecimal shippingYen,
        BigDecimal weightKg,
        String notes) {
}
