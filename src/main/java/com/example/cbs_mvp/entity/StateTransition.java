package com.example.cbs_mvp.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "state_transitions")
@Getter @Setter
@NoArgsConstructor
public class StateTransition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "log_id")
    private Long logId;

    @Column(name = "entity_type", nullable = false, length = 30)
    private String entityType; // 'CANDIDATE','ORDER','PO','SYSTEM'

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Column(name = "from_state")
    private String fromState;

    @Column(name = "to_state", nullable = false)
    private String toState;

    @Column(name = "reason_code")
    private String reasonCode;

    @Column(name = "reason_detail")
    private String reasonDetail;

    @Column(name = "actor", nullable = false, length = 50)
    private String actor; // 'SYSTEM','USER','BATCH'

    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (actor == null || actor.isBlank()) actor = "SYSTEM";
    }
}
