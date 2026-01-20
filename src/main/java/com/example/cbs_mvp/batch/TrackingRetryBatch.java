package com.example.cbs_mvp.batch;

import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.cbs_mvp.entity.Order;
import com.example.cbs_mvp.ops.KillSwitchService;
import com.example.cbs_mvp.ops.SystemFlagService;
import com.example.cbs_mvp.repo.OrderRepository;
import com.example.cbs_mvp.service.TrackingService;

@Component
public class TrackingRetryBatch {

    private static final Logger log = LoggerFactory.getLogger(TrackingRetryBatch.class);
    private static final int RETRY_LIMIT_DEFAULT = 20;
    private static final int RETRY_MAX_ATTEMPTS_DEFAULT = 5;
    private static final int RETRY_MAX_AGE_MINUTES_DEFAULT = 60;

    private final KillSwitchService killSwitch;
    private final OrderRepository orderRepo;
    private final TrackingService trackingService;
    private final SystemFlagService flags;

    public TrackingRetryBatch(
            KillSwitchService killSwitch,
            OrderRepository orderRepo,
            TrackingService trackingService,
            SystemFlagService flags
    ) {
        this.killSwitch = killSwitch;
        this.orderRepo = orderRepo;
        this.trackingService = trackingService;
        this.flags = flags;
    }

    @Scheduled(fixedRate = 30_000)
    public void run() {
        if (killSwitch.isPaused()) {
            log.warn("[TrackingRetryBatch] skipped: SYSTEM IS PAUSED");
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        int limit = positiveFlagInt("EBAY_TRACKING_RETRY_BATCH_LIMIT", RETRY_LIMIT_DEFAULT);
        List<Order> targets = orderRepo.findTrackingRetryTargets(
                "3PL_SHIPPED_INTL",
                now,
                PageRequest.of(0, limit)
        );
        if (targets.isEmpty()) {
            return;
        }

        int maxAttempts = positiveFlagInt(
                "EBAY_TRACKING_RETRY_MAX_ATTEMPTS",
                RETRY_MAX_ATTEMPTS_DEFAULT
        );
        int maxAgeMinutes = positiveFlagInt(
                "EBAY_TRACKING_RETRY_MAX_AGE_MINUTES",
                RETRY_MAX_AGE_MINUTES_DEFAULT
        );

        int attempted = 0;
        int terminal = 0;
        int errors = 0;

        for (Order order : targets) {
            if (shouldStopRetry(order, now, maxAttempts, maxAgeMinutes)) {
                trackingService.markTrackingUploadFailedTerminal(
                        order.getOrderId(),
                        terminalDetail(order)
                );
                terminal++;
                continue;
            }
            try {
                trackingService.uploadTracking(order.getOrderId());
                attempted++;
            } catch (RuntimeException ex) {
                errors++;
                log.warn("[TrackingRetryBatch] upload failed orderId={}", order.getOrderId(), ex);
            }
        }

        if (attempted > 0 || terminal > 0 || errors > 0) {
            log.info("[TrackingRetryBatch] attempted={} terminal={} errors={}", attempted, terminal, errors);
        }
    }

    private static boolean shouldStopRetry(
            Order order,
            LocalDateTime now,
            int maxAttempts,
            int maxAgeMinutes
    ) {
        if (order.getTrackingRetryCount() >= maxAttempts) {
            return true;
        }
        LocalDateTime startedAt = order.getTrackingRetryStartedAt();
        return startedAt != null && startedAt.plusMinutes(maxAgeMinutes).isBefore(now);
    }

    private static String terminalDetail(Order order) {
        String startedAt = order.getTrackingRetryStartedAt() == null
                ? ""
                : order.getTrackingRetryStartedAt().toString();
        String lastError = order.getTrackingRetryLastError() == null
                ? ""
                : order.getTrackingRetryLastError();
        return "retry_exhausted count=" + order.getTrackingRetryCount()
                + " startedAt=" + startedAt
                + " lastError=" + lastError;
    }

    private int positiveFlagInt(String key, int def) {
        int v = flagInt(key, def);
        return v > 0 ? v : def;
    }

    private int flagInt(String key, int def) {
        String v = flags.get(key);
        if (v == null || v.isBlank()) return def;
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException ex) {
            return def;
        }
    }
}
