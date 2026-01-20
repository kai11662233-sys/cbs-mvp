package com.example.cbs_mvp.service;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.cbs_mvp.ebay.EbayOrderClient;
import com.example.cbs_mvp.ebay.EbayOrderClientException;
import com.example.cbs_mvp.entity.Fulfillment;
import com.example.cbs_mvp.entity.Order;
import com.example.cbs_mvp.ops.KillSwitchService;
import com.example.cbs_mvp.ops.SystemFlagService;
import com.example.cbs_mvp.repo.FulfillmentRepository;
import com.example.cbs_mvp.repo.OrderRepository;

@Service
public class TrackingService {

    private final OrderRepository orderRepo;
    private final FulfillmentRepository fulfillmentRepo;
    private final EbayOrderClient ebayOrderClient;
    private final KillSwitchService killSwitch;
    private final SystemFlagService flags;
    private final StateTransitionService transitions;

    private static final long RETRY_BASE_DELAY_SECONDS_DEFAULT = 60;
    private static final long RETRY_MAX_DELAY_SECONDS_DEFAULT = 900;

    public TrackingService(
            OrderRepository orderRepo,
            FulfillmentRepository fulfillmentRepo,
            EbayOrderClient ebayOrderClient,
            KillSwitchService killSwitch,
            SystemFlagService flags,
            StateTransitionService transitions
    ) {
        this.orderRepo = orderRepo;
        this.fulfillmentRepo = fulfillmentRepo;
        this.ebayOrderClient = ebayOrderClient;
        this.killSwitch = killSwitch;
        this.flags = flags;
        this.transitions = transitions;
    }

    @Transactional(noRollbackFor = EbayOrderClientException.class)
    public Order uploadTracking(Long orderId) {
        if (killSwitch.isPaused()) {
            throw new IllegalStateException("system is paused");
        }

        Order order = orderRepo.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("order not found"));

        if ("EBAY_TRACKING_UPLOADED".equals(order.getState())) {
            clearTrackingRetry(order);
            orderRepo.save(order);
            return order;
        }
        if (order.getTrackingRetryTerminalAt() != null) {
            return order;
        }

        if (!"3PL_SHIPPED_INTL".equals(order.getState())) {
            throw new IllegalStateException("order not ready: state=" + order.getState());
        }

        Fulfillment f = fulfillmentRepo.findByOrderId(orderId)
                .orElseThrow(() -> new IllegalStateException("fulfillment not found"));

        String carrier = nz(f.getOutboundCarrier());
        String tracking = f.getOutboundTracking();
        if (tracking == null || tracking.isBlank()) {
            throw new IllegalStateException("tracking missing");
        }
        if (carrier.isBlank()) {
            carrier = "UNKNOWN";
        }

        String from = order.getState();
        String orderKey = order.getEbayOrderKey();

        try {
            ebayOrderClient.uploadTracking(orderKey, carrier, tracking);

            order.setState("EBAY_TRACKING_UPLOADED");
            clearTrackingRetry(order);
            orderRepo.save(order);

            transitions.log("ORDER", orderId, from, order.getState(),
                    "EBAY_TRACKING_UPLOADED", null, "SYSTEM", cid());
            return order;
        } catch (EbayOrderClientException ex) {
            boolean recovered = false;
            if (shouldCheckOnError(ex)) {
                boolean uploaded = ebayOrderClient.checkTrackingUploaded(orderKey);
                if (uploaded) {
                    order.setState("EBAY_TRACKING_UPLOADED");
                    clearTrackingRetry(order);
                    orderRepo.save(order);

                    transitions.log("ORDER", orderId, from, order.getState(),
                            "EBAY_TRACKING_UPLOADED_RECOVERED",
                            detail(orderKey, carrier, tracking, ex.getMessage()),
                            "SYSTEM",
                            cid());
                    recovered = true;
                }
            }
            if (recovered) {
                // Policy: only emit FAILED logs when recovery fails (avoid false auto-pause).
                return order;
            }
            String reasonCode;
            if (isTerminalTrackingFailure(ex)) {
                recordTerminalFailure(order, ex.getMessage(), true);
                orderRepo.save(order);
                reasonCode = "EBAY_TRACKING_UPLOAD_FAILED";
            } else {
                scheduleTrackingRetry(order, ex.getMessage());
                orderRepo.save(order);
                reasonCode = "EBAY_TRACKING_UPLOAD_RETRYING";
            }
            transitions.log(
                    "ORDER",
                    orderId,
                    from,
                    order.getState(),
                    reasonCode,
                    ex.getMessage(),
                    "SYSTEM",
                    cid()
            );
            throw ex;
        }
    }

    @Transactional
    public void markTrackingUploadFailedTerminal(Long orderId, String reasonDetail) {
        Order order = orderRepo.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("order not found"));

        String state = order.getState();
        if (isTrackingUploadComplete(state)) {
            return;
        }
        if (!"3PL_SHIPPED_INTL".equals(state)) {
            throw new IllegalStateException("order not ready: state=" + state);
        }
        if (order.getTrackingRetryTerminalAt() != null) {
            return;
        }

        recordTerminalFailure(order, reasonDetail, false);
        orderRepo.save(order);

        transitions.log(
                "ORDER",
                orderId,
                state,
                state,
                "EBAY_TRACKING_UPLOAD_FAILED",
                reasonDetail,
                "SYSTEM",
                cid()
        );
    }

    private boolean shouldCheckOnError(EbayOrderClientException ex) {
        String v = flags.get("EBAY_TRACKING_CHECK_ON_ERROR");
        if (v != null && !v.isBlank()) {
            return "true".equalsIgnoreCase(v.trim());
        }
        return ex.isRetryable();
    }

    private static String detail(String orderKey, String carrier, String tracking, String msg) {
        return "orderKey=" + nz(orderKey) + ";carrier=" + nz(carrier)
                + ";tracking=" + nz(tracking) + ";error=" + nz(msg);
    }

    private static String cid() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static String nz(String v) {
        return v == null ? "" : v.trim();
    }

    private long nextRetryDelaySeconds(int attempt) {
        long base = positiveFlagLong(
                "EBAY_TRACKING_RETRY_BASE_DELAY_SECONDS",
                RETRY_BASE_DELAY_SECONDS_DEFAULT
        );
        long max = positiveFlagLong(
                "EBAY_TRACKING_RETRY_MAX_DELAY_SECONDS",
                RETRY_MAX_DELAY_SECONDS_DEFAULT
        );
        if (max < base) max = base;
        if (attempt <= 1) return base;
        long delay = base * (1L << (attempt - 1));
        return Math.min(delay, max);
    }

    private void scheduleTrackingRetry(Order order, String reasonDetail) {
        int count = Math.max(0, order.getTrackingRetryCount()) + 1;
        LocalDateTime now = LocalDateTime.now();
        if (order.getTrackingRetryStartedAt() == null) {
            order.setTrackingRetryStartedAt(now);
        }
        order.setTrackingRetryCount(count);
        order.setTrackingRetryLastError(nz(reasonDetail));
        order.setTrackingNextRetryAt(now.plusSeconds(nextRetryDelaySeconds(count)));
    }

    private void recordTerminalFailure(Order order, String reasonDetail, boolean countAttempt) {
        LocalDateTime now = LocalDateTime.now();
        if (order.getTrackingRetryStartedAt() == null) {
            order.setTrackingRetryStartedAt(now);
        }
        if (countAttempt) {
            order.setTrackingRetryCount(Math.max(0, order.getTrackingRetryCount()) + 1);
        }
        order.setTrackingRetryLastError(nz(reasonDetail));
        order.setTrackingNextRetryAt(null);
        if (order.getTrackingRetryTerminalAt() == null) {
            order.setTrackingRetryTerminalAt(now);
        }
    }

    private void clearTrackingRetry(Order order) {
        order.setTrackingRetryCount(0);
        order.setTrackingRetryStartedAt(null);
        order.setTrackingNextRetryAt(null);
        order.setTrackingRetryLastError(null);
        order.setTrackingRetryTerminalAt(null);
    }

    private long positiveFlagLong(String key, long def) {
        long v = flagLong(key, def);
        return v > 0 ? v : def;
    }

    private long flagLong(String key, long def) {
        String v = flags.get(key);
        if (v == null || v.isBlank()) return def;
        try {
            return Long.parseLong(v.trim());
        } catch (NumberFormatException ex) {
            return def;
        }
    }

    private static boolean isTrackingUploadComplete(String state) {
        return "EBAY_TRACKING_UPLOADED".equals(state)
                || "DELIVERED".equals(state)
                || "CLAIM".equals(state);
    }

    private static boolean isTerminalTrackingFailure(EbayOrderClientException ex) {
        return !ex.isRetryable();
    }
}
