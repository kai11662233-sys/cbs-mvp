package com.example.cbs_mvp.repo;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.cbs_mvp.entity.Order;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByState(String state, Pageable pageable);
    Optional<Order> findByEbayOrderKey(String ebayOrderKey);

    @Query("""
            SELECT DISTINCT o FROM Order o
            JOIN Fulfillment f ON f.orderId = o.orderId
            WHERE o.state = :state
              AND o.trackingRetryTerminalAt IS NULL
              AND (o.trackingNextRetryAt IS NULL OR o.trackingNextRetryAt <= :now)
              AND f.outboundTracking IS NOT NULL
              AND f.outboundTracking <> ''
            ORDER BY o.updatedAt ASC
            """)
    List<Order> findTrackingRetryTargets(
            @Param("state") String state,
            @Param("now") LocalDateTime now,
            Pageable pageable
    );
}
