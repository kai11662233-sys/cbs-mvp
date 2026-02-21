package com.example.cbs_mvp.discovery;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DiscoveryItemRepository extends JpaRepository<DiscoveryItem, Long> {

  /**
   * おすすめ一覧取得（フィルタ対応）
   * excludeUsed: trueならUSED以外のみ
   * minSafety: 最低安全スコア
   * minProfit: 最低利益スコア
   */
  @Query("""
      SELECT d FROM DiscoveryItem d
      WHERE (:excludeUsed = false OR d.condition <> 'USED')
        AND d.safetyScore >= :minSafety
        AND d.profitScore >= :minProfit
        AND d.status NOT IN ('ARCHIVED', 'DRAFTED')
      ORDER BY d.overallScore DESC
      """)
  List<DiscoveryItem> findRecommendations(
      @Param("excludeUsed") boolean excludeUsed,
      @Param("minSafety") int minSafety,
      @Param("minProfit") int minProfit,
      Pageable pageable);

  /**
   * ステータス別カウント
   */
  long countByStatus(String status);

  /**
   * 条件別カウント
   */
  long countByCondition(String condition);

  /**
   * sourceUrlで検索（upsert判定用）
   */
  java.util.Optional<DiscoveryItem> findBySourceUrl(String sourceUrl);

  /**
   * sourceUrl重複チェック
   */
  boolean existsBySourceUrl(String sourceUrl);

  /**
   * タイトル完全一致 + 価格帯での疑似重複チェック
   */
  @Query("""
      SELECT d FROM DiscoveryItem d
      WHERE d.title = :title
        AND d.priceYen BETWEEN :priceLow AND :priceHigh
        AND d.status NOT IN ('ARCHIVED')
      ORDER BY d.updatedAt DESC
      """)
  List<DiscoveryItem> findByTitleAndPriceRange(
      @Param("title") String title,
      @Param("priceLow") java.math.BigDecimal priceLow,
      @Param("priceHigh") java.math.BigDecimal priceHigh);

  /**
   * linked_candidate_idで検索
   */
  java.util.Optional<DiscoveryItem> findByLinkedCandidateId(Long candidateId);

  /**
   * ステータス一覧取得
   */
  List<DiscoveryItem> findByStatusIn(List<String> statuses, Pageable pageable);
}
