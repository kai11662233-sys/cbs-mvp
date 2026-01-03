package com.example.cbs_mvp.pricing;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PricingInput {
    @NotNull
    private BigDecimal sourcePriceYen; // B列

    private BigDecimal weightKg;       // C列（null可）

    private String sizeTier;           // D列（null可）

    @NotNull
    private BigDecimal fxRate;         // Params fx_rate

    private BigDecimal targetSellUsd;  // E列（null可）
}
