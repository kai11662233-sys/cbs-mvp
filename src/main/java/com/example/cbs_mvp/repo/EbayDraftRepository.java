package com.example.cbs_mvp.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.example.cbs_mvp.entity.EbayDraft;

@Repository
public interface EbayDraftRepository extends JpaRepository<EbayDraft, Long> {
    Optional<EbayDraft> findBySku(String sku);
    Optional<EbayDraft> findByCandidateId(Long candidateId);

    @Query("SELECT d FROM EbayDraft d ORDER BY d.updatedAt DESC")
    List<EbayDraft> findRecent(Pageable pageable);
}
