package com.example.cbs_mvp.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.cbs_mvp.entity.StateTransition;

@Repository
public interface StateTransitionRepository extends JpaRepository<StateTransition, Long> {}
