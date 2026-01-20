package com.example.cbs_mvp.repo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.cbs_mvp.entity.CashLedger;

@Repository
public interface CashLedgerRepository extends JpaRepository<CashLedger, Long> {
    Optional<CashLedger> findByRefTableAndRefIdAndEventType(String refTable, Long refId, String eventType);

    @Query("""
            SELECT COALESCE(SUM(c.amountYen), 0)
            FROM CashLedger c
            WHERE c.eventType = :eventType
              AND c.actualDate IS NOT NULL
              AND c.actualDate >= :fromDate
            """)
    BigDecimal sumAmountByEventTypeSince(
            @Param("eventType") String eventType,
            @Param("fromDate") LocalDate fromDate
    );
}
