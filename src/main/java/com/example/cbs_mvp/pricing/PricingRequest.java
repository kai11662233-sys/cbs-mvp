package com.example.cbs_mvp.pricing;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PricingRequest {

    @NotNull
    private BigDecimal sourcePriceYen; // B列

    private BigDecimal weightKg; // C列（null可）
    private String sizeTier; // D列（null可: S/M/L/XL）
    @NotNull
    private BigDecimal fxRate; // Params fx_rate

    private BigDecimal targetSellUsd; // E列（null可）
    private Long candidateId; // Optional: For history comparison
}
