package com.example.cbs_mvp.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Entity
@Table(name = "pricing_results_history")
@EntityListeners(AuditingEntityListener.class)
public class PricingResultHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "history_id")
    private Long historyId;

    @Column(name = "candidate_id", nullable = false)
    private Long candidateId;

    @Column(name = "pricing_id")
    private Long pricingId;

    @Column(name = "fx_rate")
    private BigDecimal fxRate;

    @Column(name = "sell_price_usd")
    private BigDecimal sellPriceUsd;

    @Column(name = "total_cost_yen")
    private BigDecimal totalCostYen;

    @Column(name = "profit_yen")
    private BigDecimal profitYen;

    @Column(name = "profit_rate")
    private BigDecimal profitRate;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
