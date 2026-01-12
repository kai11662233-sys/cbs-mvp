package com.example.cbs_mvp.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "pricing_results")
@Getter
@Setter
@NoArgsConstructor
public class PricingResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pricing_id")
    private Long pricingId;

    @Column(name = "candidate_id", nullable = false, unique = true)
    private Long candidateId;

    @Column(name = "fx_rate", nullable = false, precision = 10, scale = 4)
    private BigDecimal fxRate;

    @Column(name = "fx_safe", nullable = false, precision = 10, scale = 4)
    private BigDecimal fxSafe;

    @Column(name = "sell_price_usd", nullable = false, precision = 12, scale = 2)
    private BigDecimal sellPriceUsd;

    @Column(name = "sell_price_yen", nullable = false, precision = 12, scale = 2)
    private BigDecimal sellPriceYen;

    @Column(name = "total_cost_yen", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalCostYen;

    @Column(name = "ebay_fee_yen", nullable = false, precision = 12, scale = 2)
    private BigDecimal ebayFeeYen;

    @Column(name = "refund_reserve_yen", nullable = false, precision = 12, scale = 2)
    private BigDecimal refundReserveYen;

    @Column(name = "profit_yen", nullable = false, precision = 12, scale = 2)
    private BigDecimal profitYen;

    @Column(name = "profit_rate", nullable = false, precision = 6, scale = 4)
    private BigDecimal profitRate;

    @Column(name = "gate_profit_ok", nullable = false)
    private boolean gateProfitOk;

    @Column(name = "gate_cash_ok", nullable = false)
    private boolean gateCashOk;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
