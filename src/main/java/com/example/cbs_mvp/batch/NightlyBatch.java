package com.example.cbs_mvp.batch;

import com.example.cbs_mvp.ops.KillSwitchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class NightlyBatch {

    private static final Logger log = LoggerFactory.getLogger(NightlyBatch.class);

    private final KillSwitchService killSwitchService;

    public NightlyBatch(KillSwitchService killSwitchService) {
        this.killSwitchService = killSwitchService;
    }

    // 動作確認用：10秒ごとに実行
    @Scheduled(fixedRate = 10_000)
    public void run() {
        if (killSwitchService.isPaused()) {
            log.warn("[NightlyBatch] skipped: SYSTEM IS PAUSED");
            return;
        }
        log.info("[NightlyBatch] running... (dummy)");
    }
}
