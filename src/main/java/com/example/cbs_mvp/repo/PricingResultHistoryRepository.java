package com.example.cbs_mvp.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.cbs_mvp.entity.PricingResultHistory;

@Repository
public interface PricingResultHistoryRepository extends JpaRepository<PricingResultHistory, Long> {
    List<PricingResultHistory> findByCandidateIdOrderByCreatedAtDesc(Long candidateId);
}
