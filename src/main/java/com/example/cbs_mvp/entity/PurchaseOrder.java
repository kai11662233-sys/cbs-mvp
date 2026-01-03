package com.example.cbs_mvp.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "purchase_orders")
@Getter @Setter
@NoArgsConstructor
public class PurchaseOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "po_id")
    private Long poId;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "supplier_name")
    private String supplierName;

    @Column(name = "supplier_order_ref")
    private String supplierOrderRef;

    @Column(name = "ship_to_3pl_address", nullable = false)
    private String shipTo3plAddress;

    @Column(name = "inbound_tracking")
    private String inboundTracking;

    @Column(name = "expected_total_cost_yen", nullable = false, precision = 12, scale = 2)
    private BigDecimal expectedTotalCostYen;

    @Column(name = "state", nullable = false, length = 30)
    private String state;

    @Column(name = "fail_reason")
    private String failReason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        var now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
