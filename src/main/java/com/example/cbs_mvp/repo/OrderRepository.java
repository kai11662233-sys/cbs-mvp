package com.example.cbs_mvp.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.cbs_mvp.entity.Order;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
}
