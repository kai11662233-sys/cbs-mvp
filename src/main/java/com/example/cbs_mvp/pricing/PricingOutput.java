package com.example.cbs_mvp.pricing;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

@Value
@Builder
public class PricingOutput {
    BigDecimal safeWeightKg;
    String safeSizeTier;
    BigDecimal fxSafe;
    BigDecimal intlShipCostYen; // L
    BigDecimal expectedCostJpy; // M
    BigDecimal recSellUsd; // I
    BigDecimal useSellUsd; // J
    BigDecimal sellYen; // K
    BigDecimal feesAndReserveYen; // N
    BigDecimal expectedProfitJpy; // O
    BigDecimal profitRate; // P (表示用)
    boolean gateProfitOk; // Q
}
