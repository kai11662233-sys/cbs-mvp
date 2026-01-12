package com.example.cbs_mvp.batch;

import java.util.List;

import com.example.cbs_mvp.entity.EbayDraft;
import com.example.cbs_mvp.ops.KillSwitchService;
import com.example.cbs_mvp.repo.EbayDraftRepository;
import com.example.cbs_mvp.service.DraftService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class NightlyBatch {

    private static final Logger log = LoggerFactory.getLogger(NightlyBatch.class);

    private final KillSwitchService killSwitchService;
    private final DraftService draftService;
    private final EbayDraftRepository draftRepo;

    public NightlyBatch(
            KillSwitchService killSwitchService,
            DraftService draftService,
            EbayDraftRepository draftRepo
    ) {
        this.killSwitchService = killSwitchService;
        this.draftService = draftService;
        this.draftRepo = draftRepo;
    }

    // 動作確認用：10秒ごとに実行
    @Scheduled(fixedRate = 10_000)
    public void run() {
        if (killSwitchService.isPaused()) {
            log.warn("[NightlyBatch] skipped: SYSTEM IS PAUSED");
            return;
        }

        int created = draftService.processReadyCandidates(10);
        log.info("[NightlyBatch] drafts created: {}", created);

        List<EbayDraft> recent = draftRepo.findRecent(PageRequest.of(0, 10));
        long failed = recent.stream().filter(d -> "EBAY_DRAFT_FAILED".equals(d.getState())).count();
        if (failed >= 5) {
            killSwitchService.pauseFromBatch("Too many draft failures: " + failed + "/10");
        }
    }
}
