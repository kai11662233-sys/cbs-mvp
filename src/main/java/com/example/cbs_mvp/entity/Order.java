package com.example.cbs_mvp.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "ebay_order_key", nullable = false, length = 64)
    private String ebayOrderKey;

    @Column(name = "draft_id")
    private Long draftId;

    @Column(name = "sold_price_usd", nullable = false, precision = 12, scale = 2)
    private BigDecimal soldPriceUsd;

    @Column(name = "sold_price_yen", nullable = false, precision = 12, scale = 2)
    private BigDecimal soldPriceYen;

    @Column(name = "state", nullable = false, length = 30)
    private String state;

    @Column(name = "tracking_retry_count", nullable = false)
    private int trackingRetryCount;

    @Column(name = "tracking_retry_started_at")
    private LocalDateTime trackingRetryStartedAt;

    @Column(name = "tracking_next_retry_at")
    private LocalDateTime trackingNextRetryAt;

    @Column(name = "tracking_retry_last_error")
    private String trackingRetryLastError;

    @Column(name = "tracking_retry_terminal_at")
    private LocalDateTime trackingRetryTerminalAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
