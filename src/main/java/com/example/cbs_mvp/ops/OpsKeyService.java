package com.example.cbs_mvp.ops;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class OpsKeyService {

    public static final String KEY_OPS = "OPS_KEY";

    private final SystemFlagRepository repo;

    public OpsKeyService(SystemFlagRepository repo) {
        this.repo = repo;
    }

    public boolean isValid(String provided) {
        if (provided == null || provided.isBlank()) return false;
        String expected = repo.findById(KEY_OPS).map(SystemFlag::getValue).orElse(null);
        return expected != null && expected.equals(provided);
    }

    /** dev用：OPS_KEYが無いなら初期値を入れる（本番は外す） */
    @Transactional
    public void ensureDefaultOpsKeyIfMissing(String defaultKey) {
        repo.findById(KEY_OPS).ifPresentOrElse(
            f -> {}, // 既にある
            () -> {
                SystemFlag f = new SystemFlag(KEY_OPS);
                f.setValue(defaultKey);
                f.setUpdatedAt(LocalDateTime.now());
                repo.save(f);
            }
        );
    }
}
