package com.example.cbs_mvp.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.cbs_mvp.ebay.EbayOrderClient;
import com.example.cbs_mvp.entity.Fulfillment;
import com.example.cbs_mvp.entity.Order;
import com.example.cbs_mvp.ops.KillSwitchService;
import com.example.cbs_mvp.repo.FulfillmentRepository;
import com.example.cbs_mvp.repo.OrderRepository;

@Service
public class TrackingService {

    private final OrderRepository orderRepo;
    private final FulfillmentRepository fulfillmentRepo;
    private final EbayOrderClient ebayOrderClient;
    private final KillSwitchService killSwitch;
    private final StateTransitionService transitions;

    public TrackingService(
            OrderRepository orderRepo,
            FulfillmentRepository fulfillmentRepo,
            EbayOrderClient ebayOrderClient,
            KillSwitchService killSwitch,
            StateTransitionService transitions
    ) {
        this.orderRepo = orderRepo;
        this.fulfillmentRepo = fulfillmentRepo;
        this.ebayOrderClient = ebayOrderClient;
        this.killSwitch = killSwitch;
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

        String tracking = f.getOutboundTracking();
        if (tracking == null || tracking.isBlank()) {
            throw new IllegalStateException("tracking missing");
        }

        String from = order.getState();
        String orderKey = order.getEbayOrderKey();

        try {
            ebayOrderClient.uploadTracking(orderKey, f.getOutboundCarrier(), tracking);

            order.setState("EBAY_TRACKING_UPLOADED");
            orderRepo.save(order);

            transitions.log("ORDER", orderId, from, order.getState(),
                    "EBAY_TRACKING_UPLOADED", null, "SYSTEM", cid());
            return order;
        } catch (RuntimeException ex) {
            boolean uploaded = ebayOrderClient.checkTrackingUploaded(orderKey);
            if (uploaded) {
                order.setState("EBAY_TRACKING_UPLOADED");
                orderRepo.save(order);

                transitions.log("ORDER", orderId, from, order.getState(),
                        "EBAY_TRACKING_UPLOADED_RECOVERED", ex.getMessage(), "SYSTEM", cid());
                return order;
            }
            throw ex;
        }
    }

    private static String cid() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
