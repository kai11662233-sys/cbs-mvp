package com.example.cbs_mvp.service;

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

    @Transactional
    public Order uploadTracking(Long orderId) {
        if (killSwitch.isPaused()) {
            throw new IllegalStateException("system is paused");
        }

        Order order = orderRepo.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("order not found"));

        if ("EBAY_TRACKING_UPLOADED".equals(order.getState())) {
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
            orderRepo.save(order);

            transitions.log("ORDER", orderId, from, order.getState(),
                    "EBAY_TRACKING_UPLOADED", null, "SYSTEM", cid());
            return order;
        } catch (EbayOrderClientException ex) {
            if (shouldCheckOnError(ex)) {
                boolean uploaded = ebayOrderClient.checkTrackingUploaded(orderKey);
                if (uploaded) {
                    order.setState("EBAY_TRACKING_UPLOADED");
                    orderRepo.save(order);

                    transitions.log("ORDER", orderId, from, order.getState(),
                            "EBAY_TRACKING_UPLOADED_RECOVERED",
                            detail(orderKey, carrier, tracking, ex.getMessage()),
                            "SYSTEM",
                            cid());
                    return order;
                }
            }
            throw ex;
        }
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
}
