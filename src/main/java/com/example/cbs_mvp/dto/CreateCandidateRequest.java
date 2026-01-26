package com.example.cbs_mvp.dto;

import java.math.BigDecimal;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateCandidateRequest {
    @NotBlank(message = "sourceUrl is required")
    private String sourceUrl;

    @NotNull(message = "sourcePriceYen is required")
    private BigDecimal sourcePriceYen;

    private BigDecimal weightKg;
    private String sizeTier;
    private String memo;
}
