package com.example.cbs_mvp.dto;

import java.math.BigDecimal;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CandidatePricingRequest {
    @NotNull(message = "fxRate is required")
    private BigDecimal fxRate;

    private BigDecimal targetSellUsd;
    private Boolean autoDraft;
}
