package com.example.cbs_mvp.repo;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.cbs_mvp.entity.CashLedger;

@Repository
public interface CashLedgerRepository extends JpaRepository<CashLedger, Long> {
    Optional<CashLedger> findByRefTableAndRefIdAndEventType(String refTable, Long refId, String eventType);
}
