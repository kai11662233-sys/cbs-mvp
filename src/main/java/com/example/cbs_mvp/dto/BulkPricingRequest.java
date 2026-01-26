package com.example.cbs_mvp.dto;

import java.math.BigDecimal;
import java.util.List;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class BulkPricingRequest {
    @NotEmpty(message = "candidateIds cannot be empty")
    private List<Long> candidateIds;

    @NotNull(message = "fxRate is required")
    private BigDecimal fxRate;

    private Boolean autoDraft;
}
