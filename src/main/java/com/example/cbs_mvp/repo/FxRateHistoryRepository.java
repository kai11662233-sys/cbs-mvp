package com.example.cbs_mvp.repo;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.example.cbs_mvp.entity.FxRateHistory;

@Repository
public interface FxRateHistoryRepository extends JpaRepository<FxRateHistory, Long> {

        /**
         * 最新のレート取得
         */
        Optional<FxRateHistory> findTopByBaseCurrencyAndTargetCurrencyOrderByFetchedAtDesc(
                        String baseCurrency, String targetCurrency);

        /**
         * 指定期間のレート履歴
         */
        List<FxRateHistory> findByBaseCurrencyAndTargetCurrencyAndFetchedAtBetweenOrderByFetchedAtDesc(
                        String baseCurrency, String targetCurrency, Instant from, Instant to);

        /**
         * 異常レートの一覧（最新100件）
         */
        List<FxRateHistory> findTop100ByAnomalyTrueOrderByFetchedAtDesc();

        /**
         * 直近24時間の平均レート
         */
        @Query("SELECT AVG(r.rate) FROM FxRateHistory r " +
                        "WHERE r.baseCurrency = :base AND r.targetCurrency = :target " +
                        "AND r.fetchedAt > :since")
        Optional<BigDecimal> findAverageRateSince(
                        @org.springframework.data.repository.query.Param("base") String base,
                        @org.springframework.data.repository.query.Param("target") String target,
                        @org.springframework.data.repository.query.Param("since") Instant since);
}
