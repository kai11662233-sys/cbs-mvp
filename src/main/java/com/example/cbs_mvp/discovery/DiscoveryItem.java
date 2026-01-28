package com.example.cbs_mvp.discovery;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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
@Table(name = "discovery_items")
@Getter
@Setter
@NoArgsConstructor
public class DiscoveryItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_url", nullable = false)
    private String sourceUrl;

    @Column(name = "source_domain")
    private String sourceDomain;

    @Column(name = "source_type")
    private String sourceType = "OTHER"; // OFFICIAL/RETAIL/MALL/AMAZON/C2C/OTHER

    @Column(name = "title")
    private String title;

    @Column(name = "condition")
    private String condition = "UNKNOWN"; // NEW/USED/UNKNOWN

    @Column(name = "category_hint")
    private String categoryHint;

    @Column(name = "price_yen", nullable = false, precision = 12, scale = 2)
    private BigDecimal priceYen;

    @Column(name = "shipping_yen", precision = 12, scale = 2)
    private BigDecimal shippingYen;

    @Column(name = "weight_kg", precision = 6, scale = 3)
    private BigDecimal weightKg;

    @Column(name = "safety_score", nullable = false)
    private Integer safetyScore = 100;

    @Column(name = "profit_score", nullable = false)
    private Integer profitScore = 0;

    @Column(name = "freshness_score", nullable = false)
    private Integer freshnessScore = 0;

    @Column(name = "overall_score", nullable = false)
    private Integer overallScore = 0;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "risk_flags", columnDefinition = "jsonb")
    private List<String> riskFlags = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "safety_breakdown", columnDefinition = "jsonb")
    private List<Map<String, Object>> safetyBreakdown = new ArrayList<>();

    @Column(name = "last_checked_at")
    private OffsetDateTime lastCheckedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "snapshot", columnDefinition = "jsonb")
    private Map<String, Object> snapshot = new HashMap<>();

    @Column(name = "status", nullable = false)
    private String status = "NEW"; // NEW/CHECKED/OK/NG/DRAFTED/ARCHIVED

    @Column(name = "linked_candidate_id")
    private Long linkedCandidateId;

    @Column(name = "linked_draft_id")
    private Long linkedDraftId;

    @Column(name = "notes")
    private String notes;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void prePersist() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null)
            createdAt = now;
        if (updatedAt == null)
            updatedAt = now;
        if (sourceType == null)
            sourceType = "OTHER";
        if (condition == null)
            condition = "UNKNOWN";
        if (status == null)
            status = "NEW";
        if (riskFlags == null)
            riskFlags = new ArrayList<>();
        if (safetyBreakdown == null)
            safetyBreakdown = new ArrayList<>();
        if (snapshot == null)
            snapshot = new HashMap<>();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    // Helper: 禁止カテゴリがあるかどうか
    public boolean hasRestrictedCategory() {
        return riskFlags != null && riskFlags.contains("RESTRICTED_CATEGORY");
    }

    // Helper: Draft可能かどうか
    public boolean isDraftable() {
        return !hasRestrictedCategory()
                && safetyScore != null && safetyScore >= 50
                && !"NG".equals(status)
                && !"ARCHIVED".equals(status);
    }
}
