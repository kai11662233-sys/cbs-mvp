package com.example.cbs_mvp.repo;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.cbs_mvp.entity.StateTransition;

@Repository
public interface StateTransitionRepository extends JpaRepository<StateTransition, Long> {

    @Query("""
            SELECT s FROM StateTransition s
            WHERE s.entityType = :entityType
              AND s.reasonCode = :reasonCode
            ORDER BY s.createdAt DESC
            """)
    List<StateTransition> findRecentByEntityTypeAndReasonCode(
            @Param("entityType") String entityType,
            @Param("reasonCode") String reasonCode,
            Pageable pageable
    );

    @Query("""
            SELECT COUNT(DISTINCT s.entityId) FROM StateTransition s
            WHERE s.entityType = :entityType
              AND s.reasonCode = :reasonCode
              AND s.createdAt >= :fromTime
            """)
    long countDistinctEntityIdByEntityTypeAndReasonCodeSince(
            @Param("entityType") String entityType,
            @Param("reasonCode") String reasonCode,
            @Param("fromTime") LocalDateTime fromTime
    );
}
