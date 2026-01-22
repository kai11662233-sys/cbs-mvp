package com.example.cbs_mvp.entity;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Data;

/**
 * FXレート履歴 - レートの変動を追跡
 */
@Data
@Entity
@Table(name = "fx_rate_history")
public class FxRateHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 10)
    private String baseCurrency;

    @Column(nullable = false, length = 10)
    private String targetCurrency;

    @Column(nullable = false, precision = 18, scale = 8)
    private BigDecimal rate;

    @Column(nullable = false)
    private Instant fetchedAt;

    @Column(length = 50)
    private String source; // API名など

    /**
     * 前回レートとの変化率（%）
     * nullの場合は初回レコード
     */
    @Column(precision = 10, scale = 4)
    private BigDecimal changePercent;

    /**
     * 異常検知フラグ
     * true: 急変を検知（閾値超え）
     */
    @Column(nullable = false)
    private boolean anomaly = false;

    @PrePersist
    protected void onCreate() {
        if (fetchedAt == null) {
            fetchedAt = Instant.now();
        }
    }
}
