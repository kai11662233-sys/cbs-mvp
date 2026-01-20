package com.example.cbs_mvp.batch;

import java.time.LocalDateTime;
import java.util.List;

import com.example.cbs_mvp.entity.EbayDraft;
import com.example.cbs_mvp.ops.KillSwitchService;
import com.example.cbs_mvp.repo.EbayDraftRepository;
import com.example.cbs_mvp.repo.StateTransitionRepository;
import com.example.cbs_mvp.service.DraftService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class NightlyBatch {

    private static final Logger log = LoggerFactory.getLogger(NightlyBatch.class);
    private static final int DRAFT_FAILURE_THRESHOLD = 5;
    private static final int TRACKING_FAILURE_THRESHOLD = 5;
    private static final int TRACKING_FAILURE_WINDOW_MINUTES = 15;

    private final KillSwitchService killSwitchService;
    private final DraftService draftService;
    private final EbayDraftRepository draftRepo;
    private final StateTransitionRepository transitionRepo;

    public NightlyBatch(
            KillSwitchService killSwitchService,
            DraftService draftService,
            EbayDraftRepository draftRepo,
            StateTransitionRepository transitionRepo
    ) {
        this.killSwitchService = killSwitchService;
        this.draftService = draftService;
        this.draftRepo = draftRepo;
        this.transitionRepo = transitionRepo;
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

        List<EbayDraft> recent = draftRepo.findRecentByUpdatedAt(PageRequest.of(0, 10));
        long failed = recent.stream().filter(d -> "EBAY_DRAFT_FAILED".equals(d.getState())).count();
        if (failed >= DRAFT_FAILURE_THRESHOLD) {
            String detail = "AUTO_PAUSE_DRAFT_FAILURES fail=" + failed + " window=last10";
            killSwitchService.pauseFromBatch("AUTO_PAUSE_DRAFT_FAILURES", detail);
        }

        LocalDateTime fromTime = LocalDateTime.now().minusMinutes(TRACKING_FAILURE_WINDOW_MINUTES);
        long trackingFailed = transitionRepo.countDistinctEntityIdByEntityTypeAndReasonCodeSince(
                "ORDER",
                "EBAY_TRACKING_UPLOAD_FAILED",
                fromTime
        );
        if (trackingFailed >= TRACKING_FAILURE_THRESHOLD) {
            String detail = "AUTO_PAUSE_TRACKING_FAILURES fail=" + trackingFailed
                    + " window=" + TRACKING_FAILURE_WINDOW_MINUTES + "m";
            killSwitchService.pauseFromBatch("AUTO_PAUSE_TRACKING_FAILURES", detail);
        }
    }
}
