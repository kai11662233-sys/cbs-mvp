package com.example.cbs_mvp.pricing;

import java.math.BigDecimal;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class PricingResponse {
    BigDecimal safeWeightKg;
    String safeSizeTier;
    BigDecimal fxSafe;

    BigDecimal calcSourcePriceYen; // Snapshot
    BigDecimal usedFeeRate; // Snapshot

    BigDecimal intlShipCostYen; // L
    BigDecimal expectedCostJpy; // M

    BigDecimal recSellUsd; // I
    BigDecimal useSellUsd; // J
    BigDecimal sellYen; // K

    BigDecimal feesAndReserveYen; // N
    BigDecimal expectedProfitJpy; // O
    BigDecimal profitRate; // P（表示用）

    boolean gateProfitOk; // Q
    String warn; // W（Price Low 等）

    // Comparison (Previous State)
    BigDecimal prevProfitYen;
    BigDecimal prevProfitRate;
    BigDecimal diffProfitYen;
}
