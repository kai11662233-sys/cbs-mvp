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
@Table(name = "pricing_rules")
@Getter
@Setter
@NoArgsConstructor
public class PricingRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "rule_id")
    private Long ruleId;

    @Column(name = "condition_type", nullable = false)
    private String conditionType; // SOURCE_PRICE, WEIGHT

    @Column(name = "condition_min", precision = 12, scale = 2)
    private BigDecimal conditionMin;

    @Column(name = "condition_max", precision = 12, scale = 2)
    private BigDecimal conditionMax;

    @Column(name = "target_field", nullable = false)
    private String targetField; // PROFIT_MIN_YEN, PROFIT_MIN_RATE

    @Column(name = "adjustment_value", nullable = false, precision = 12, scale = 2)
    private BigDecimal adjustmentValue;

    @Column(name = "priority", nullable = false)
    private Integer priority;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (priority == null)
            priority = 0;
        if (createdAt == null)
            createdAt = LocalDateTime.now();
    }
}
