package com.example.cbs_mvp.ops;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

import com.example.cbs_mvp.entity.StateTransition;
import com.example.cbs_mvp.repo.StateTransitionRepository;

@Service
public class KillSwitchService {

    private static final Logger log = LoggerFactory.getLogger(KillSwitchService.class);

    public static final String KEY_PAUSED = "PAUSED";
    public static final String KEY_REASON = "PAUSE_REASON";

    private final SystemFlagRepository flagRepo;
    private final StateTransitionRepository transitionRepo;

    public KillSwitchService(SystemFlagRepository flagRepo, StateTransitionRepository transitionRepo) {
        this.flagRepo = flagRepo;
        this.transitionRepo = transitionRepo;
    }

    public boolean isPaused() {
        return flagRepo.findById(KEY_PAUSED)
                .map(f -> "true".equalsIgnoreCase(nz(f.getValue())))
                .orElse(false);
    }

    public String getReason() {
        return flagRepo.findById(KEY_REASON).map(SystemFlag::getValue).orElse("");
    }

    public LocalDateTime getUpdatedAt() {
        return flagRepo.findById(KEY_PAUSED).map(SystemFlag::getUpdatedAt).orElse(null);
    }

    @Transactional
    public void pause(String reason) {
        if (isPaused()) return;
        saveFlag(KEY_PAUSED, "true");
        saveFlag(KEY_REASON, nz(reason));
        log.error("KILL SWITCH ACTIVATED: {}", reason);

        saveTransition("RUNNING", "PAUSED", "KILL_SWITCH", reason, "USER");
    }

    @Transactional
    public void resume() {
        saveFlag(KEY_PAUSED, "false");
        saveFlag(KEY_REASON, "");
        log.info("System Resumed manually.");

        saveTransition("PAUSED", "RUNNING", "RESUME", "Manual resume", "USER");
    }

    private void saveFlag(String key, String val) {
        SystemFlag f = flagRepo.findById(key).orElseGet(() -> new SystemFlag(key));
        f.setValue(val);
        f.setUpdatedAt(LocalDateTime.now());
        flagRepo.save(f);
    }

    private void saveTransition(String from, String to, String reasonCode, String reasonDetail, String actor) {
        StateTransition st = new StateTransition();
        st.setEntityType("SYSTEM");
        st.setEntityId(0L);
        st.setFromState(from);
        st.setToState(to);
        st.setReasonCode(reasonCode);
        st.setReasonDetail(reasonDetail);
        st.setActor(actor == null ? "SYSTEM" : actor);
        st.setCorrelationId(UUID.randomUUID().toString().replace("-", ""));
        st.setCreatedAt(LocalDateTime.now());
        transitionRepo.save(st);
    }

    private static String nz(String s) {
        return (s == null) ? "" : s;
    }
}
