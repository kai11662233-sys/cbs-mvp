package com.example.cbs_mvp.ops;

import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class SystemFlagService {

    private final SystemFlagRepository repo;

    public SystemFlagService(SystemFlagRepository repo) {
        this.repo = repo;
    }

    /**
     * Return the value for the key or null if not present.
     */
    public String get(String key) {
        return repo.findById(key).map(SystemFlag::getValue).orElse(null);
    }
}
