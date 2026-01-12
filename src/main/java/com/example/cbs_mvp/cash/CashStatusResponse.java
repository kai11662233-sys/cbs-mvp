package com.example.cbs_mvp.cash;

import java.math.BigDecimal;

public record CashStatusResponse(
        BigDecimal currentCash,
        BigDecimal creditLimit,
        BigDecimal creditUsed,
        BigDecimal openCommitmentsYen,
        BigDecimal refundReserveYen,
        BigDecimal wcAvailableYen,
        BigDecimal capLimitYen,
        BigDecimal capUsedYen,
        String ts
) {}
