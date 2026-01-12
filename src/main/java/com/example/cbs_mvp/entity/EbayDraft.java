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
@Table(name = "ebay_drafts")
@Getter
@Setter
@NoArgsConstructor
public class EbayDraft {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "draft_id")
    private Long draftId;

    @Column(name = "candidate_id", nullable = false)
    private Long candidateId;

    @Column(name = "sku", nullable = false, length = 64, unique = true)
    private String sku;

    @Column(name = "inventory_item_id", length = 64)
    private String inventoryItemId;

    @Column(name = "offer_id", length = 64)
    private String offerId;

    @Column(name = "marketplace", nullable = false, length = 16)
    private String marketplace;

    @Column(name = "title_en", nullable = false, length = 200)
    private String titleEn;

    @Column(name = "description_html", nullable = false)
    private String descriptionHtml;

    @Column(name = "list_price_usd", nullable = false, precision = 12, scale = 2)
    private BigDecimal listPriceUsd;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "state", nullable = false, length = 30)
    private String state;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (marketplace == null || marketplace.isBlank()) marketplace = "EBAY_US";
        if (quantity == null) quantity = 1;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
