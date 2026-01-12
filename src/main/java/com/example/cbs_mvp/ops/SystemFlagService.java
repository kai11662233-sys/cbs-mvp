package com.example.cbs_mvp.ops;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

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

    public void set(String key, String value) {
        SystemFlag f = repo.findById(key).orElseGet(() -> new SystemFlag(key));
        f.setValue(value);
        f.setUpdatedAt(LocalDateTime.now());
        repo.save(f);
    }
}
