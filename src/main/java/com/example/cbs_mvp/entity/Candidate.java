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
@Table(name = "candidates")
@Getter
@Setter
@NoArgsConstructor
public class Candidate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "candidate_id")
    private Long candidateId;

    @Column(name = "source_url", nullable = false)
    private String sourceUrl;

    @Column(name = "source_price_yen", nullable = false, precision = 12, scale = 2)
    private BigDecimal sourcePriceYen;

    @Column(name = "weight_kg", precision = 6, scale = 3)
    private BigDecimal weightKg;

    @Column(name = "size_tier", length = 5, nullable = false)
    private String sizeTier;

    @Column(name = "memo")
    private String memo;

    @Column(name = "state", length = 30, nullable = false)
    private String state;

    @Column(name = "reject_reason_code", length = 50)
    private String rejectReasonCode;

    @Column(name = "reject_reason_detail")
    private String rejectReasonDetail;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "last_calculated_at")
    private LocalDateTime lastCalculatedAt;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null)
            createdAt = now;
        if (updatedAt == null)
            updatedAt = now;
        if (sizeTier == null || sizeTier.isBlank())
            sizeTier = "XL";
        if (state == null || state.isBlank())
            state = "CANDIDATE";
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
