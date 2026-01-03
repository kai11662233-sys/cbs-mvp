package com.example.cbs_mvp.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "cash_ledger")
@Getter @Setter
@NoArgsConstructor
public class CashLedger {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cash_id")
    private Long cashId;

    @Column(name = "event_type", nullable = false, length = 30)
    private String eventType; // SALE / PROCUREMENT / REFUND / FEE

    @Column(name = "ref_table", nullable = false, length = 30)
    private String refTable; // 'purchase_orders' etc

    @Column(name = "ref_id", nullable = false)
    private Long refId;

    @Column(name = "amount_yen", nullable = false, precision = 12, scale = 2)
    private BigDecimal amountYen; // 入金(+) / 出金(-)

    @Column(name = "expected_date")
    private LocalDate expectedDate;

    @Column(name = "actual_date")
    private LocalDate actualDate; // NULL=未確定(Commitment)

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
