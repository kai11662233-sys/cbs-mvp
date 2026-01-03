package com.example.cbs_mvp.ops;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SystemFlagRepository extends JpaRepository<SystemFlag, String> {
    Optional<SystemFlag> findByKey(String key);
}
