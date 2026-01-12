package com.example.cbs_mvp.entity;

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
@Table(name = "fulfillment")
@Getter
@Setter
@NoArgsConstructor
public class Fulfillment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "fulfill_id")
    private Long fulfillId;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "inbound_received_at")
    private LocalDateTime inboundReceivedAt;

    @Column(name = "outbound_carrier", length = 50)
    private String outboundCarrier;

    @Column(name = "outbound_tracking", length = 64)
    private String outboundTracking;

    @Column(name = "state", nullable = false, length = 30)
    private String state;

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
