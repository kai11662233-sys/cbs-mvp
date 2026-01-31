package com.example.cbs_mvp.dto.discovery;

import java.math.BigDecimal;

import jakarta.validation.constraints.Positive;

/**
 * Draft作成リクエストDTO
 */
public record DraftRequest(
        @Positive(message = "fxRate must be positive") BigDecimal fxRate,

        @Positive(message = "targetSellUsd must be positive") BigDecimal targetSellUsd) {
}
