package com.example.cbs_mvp.repo;

import java.math.BigDecimal;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;

import com.example.cbs_mvp.entity.PurchaseOrder;

import java.util.List;

@Repository
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {

    // open_commitments（Freeze: EXISTS版）
    @Query(value = """
        SELECT COALESCE(SUM(po.expected_total_cost_yen), 0) AS open_commitments_yen
        FROM purchase_orders po
        WHERE
          po.state <> 'PROCUREMENT_FAILED'
          AND (
            NOT EXISTS (
              SELECT 1 FROM cash_ledger cl
              WHERE cl.ref_table = 'purchase_orders'
                AND cl.ref_id = po.po_id
                AND cl.event_type = 'PROCUREMENT'
            )
            OR EXISTS (
              SELECT 1 FROM cash_ledger cl
              WHERE cl.ref_table = 'purchase_orders'
                AND cl.ref_id = po.po_id
                AND cl.event_type = 'PROCUREMENT'
                AND cl.actual_date IS NULL
            )
          )
        """, nativeQuery = true)
    BigDecimal calculateOpenCommitments();

    @Query("SELECT p FROM PurchaseOrder p ORDER BY p.createdAt DESC")
    List<PurchaseOrder> findRecent(Pageable pageable);
}
