package com.example.cbs_mvp.dto.discovery;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Discovery Item 作成リクエストDTO
 */
public record CreateDiscoveryItemRequest(
        @NotBlank(message = "sourceUrl is required") String sourceUrl,

        String title,

        String condition,

        String sourceType,

        String categoryHint,

        @NotNull(message = "priceYen is required") @Positive(message = "priceYen must be positive") BigDecimal priceYen,

        BigDecimal shippingYen,

        BigDecimal weightKg,

        String notes) {
}
