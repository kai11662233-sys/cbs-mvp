package com.example.cbs_mvp.repo;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.cbs_mvp.entity.PricingResult;

@Repository
public interface PricingResultRepository extends JpaRepository<PricingResult, Long> {
    Optional<PricingResult> findByCandidateId(Long candidateId);

    @org.springframework.data.jpa.repository.Query(value = """
                SELECT
                    AVG(p.profit_rate) as avgProfitRate,
                    SUM(p.profit_yen) as totalProfitYen,
                    SUM(p.sell_price_yen) as totalSalesYen
                FROM pricing_results p
                JOIN candidates c ON p.candidate_id = c.candidate_id
                WHERE c.state = :state
            """, nativeQuery = true)
    StatsSummary findStatsByState(@org.springframework.web.bind.annotation.RequestParam("state") String state);

    interface StatsSummary {
        java.math.BigDecimal getAvgProfitRate();

        java.math.BigDecimal getTotalProfitYen();

        java.math.BigDecimal getTotalSalesYen();
    }
}
