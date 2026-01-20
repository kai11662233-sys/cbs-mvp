package com.example.cbs_mvp.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.example.cbs_mvp.ebay.EbayOrderClient;
import com.example.cbs_mvp.ebay.EbayOrderClientException;
import com.example.cbs_mvp.entity.Fulfillment;
import com.example.cbs_mvp.entity.Order;
import com.example.cbs_mvp.ops.KillSwitchService;
import com.example.cbs_mvp.ops.SystemFlagService;
import com.example.cbs_mvp.repo.FulfillmentRepository;
import com.example.cbs_mvp.repo.OrderRepository;

class TrackingServiceTest {

    @Test
    void uploadTracking_recoveredDoesNotLogFailed() {
        OrderRepository orderRepo = Mockito.mock(OrderRepository.class);
        FulfillmentRepository fulfillmentRepo = Mockito.mock(FulfillmentRepository.class);
        EbayOrderClient ebayOrderClient = Mockito.mock(EbayOrderClient.class);
        KillSwitchService killSwitch = Mockito.mock(KillSwitchService.class);
        SystemFlagService flags = Mockito.mock(SystemFlagService.class);
        StateTransitionService transitions = Mockito.mock(StateTransitionService.class);

        TrackingService service = new TrackingService(
                orderRepo,
                fulfillmentRepo,
                ebayOrderClient,
                killSwitch,
                flags,
                transitions
        );

        Order order = new Order();
        order.setOrderId(1L);
        order.setEbayOrderKey("ORDER-KEY-1");
        order.setState("3PL_SHIPPED_INTL");

        Fulfillment fulfillment = new Fulfillment();
        fulfillment.setOrderId(1L);
        fulfillment.setOutboundCarrier("JapanPost");
        fulfillment.setOutboundTracking("TRACK-1");
        fulfillment.setState("3PL_SHIPPED_INTL");

        Mockito.when(killSwitch.isPaused()).thenReturn(false);
        Mockito.when(orderRepo.findById(1L)).thenReturn(Optional.of(order));
        Mockito.when(fulfillmentRepo.findByOrderId(1L)).thenReturn(Optional.of(fulfillment));
        Mockito.when(orderRepo.save(Mockito.any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        Mockito.when(flags.get("EBAY_TRACKING_CHECK_ON_ERROR")).thenReturn("true");

        Mockito.doThrow(new EbayOrderClientException("boom", true))
                .when(ebayOrderClient)
                .uploadTracking("ORDER-KEY-1", "JapanPost", "TRACK-1");
        Mockito.when(ebayOrderClient.checkTrackingUploaded("ORDER-KEY-1")).thenReturn(true);

        Order out = service.uploadTracking(1L);

        assertEquals("EBAY_TRACKING_UPLOADED", out.getState());
        Mockito.verify(transitions).log(
                Mockito.eq("ORDER"),
                Mockito.eq(1L),
                Mockito.eq("3PL_SHIPPED_INTL"),
                Mockito.eq("EBAY_TRACKING_UPLOADED"),
                Mockito.eq("EBAY_TRACKING_UPLOADED_RECOVERED"),
                Mockito.anyString(),
                Mockito.eq("SYSTEM"),
                Mockito.anyString()
        );
        Mockito.verify(transitions, Mockito.never()).log(
                Mockito.anyString(),
                Mockito.anyLong(),
                Mockito.anyString(),
                Mockito.anyString(),
                Mockito.eq("EBAY_TRACKING_UPLOAD_FAILED"),
                Mockito.anyString(),
                Mockito.anyString(),
                Mockito.anyString()
        );
    }

    @Test
    void uploadTracking_terminalFailureLogsFailedAndThrows() {
        OrderRepository orderRepo = Mockito.mock(OrderRepository.class);
        FulfillmentRepository fulfillmentRepo = Mockito.mock(FulfillmentRepository.class);
        EbayOrderClient ebayOrderClient = Mockito.mock(EbayOrderClient.class);
        KillSwitchService killSwitch = Mockito.mock(KillSwitchService.class);
        SystemFlagService flags = Mockito.mock(SystemFlagService.class);
        StateTransitionService transitions = Mockito.mock(StateTransitionService.class);

        TrackingService service = new TrackingService(
                orderRepo,
                fulfillmentRepo,
                ebayOrderClient,
                killSwitch,
                flags,
                transitions
        );

        Order order = new Order();
        order.setOrderId(2L);
        order.setEbayOrderKey("ORDER-KEY-2");
        order.setState("3PL_SHIPPED_INTL");

        Fulfillment fulfillment = new Fulfillment();
        fulfillment.setOrderId(2L);
        fulfillment.setOutboundCarrier("JapanPost");
        fulfillment.setOutboundTracking("TRACK-2");
        fulfillment.setState("3PL_SHIPPED_INTL");

        Mockito.when(killSwitch.isPaused()).thenReturn(false);
        Mockito.when(orderRepo.findById(2L)).thenReturn(Optional.of(order));
        Mockito.when(fulfillmentRepo.findByOrderId(2L)).thenReturn(Optional.of(fulfillment));
        Mockito.when(orderRepo.save(Mockito.any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        Mockito.doThrow(new EbayOrderClientException("boom", false))
                .when(ebayOrderClient)
                .uploadTracking("ORDER-KEY-2", "JapanPost", "TRACK-2");

        assertThrows(EbayOrderClientException.class, () -> service.uploadTracking(2L));

        assertNotNull(order.getTrackingRetryTerminalAt());
        Mockito.verify(transitions).log(
                Mockito.eq("ORDER"),
                Mockito.eq(2L),
                Mockito.eq("3PL_SHIPPED_INTL"),
                Mockito.eq("3PL_SHIPPED_INTL"),
                Mockito.eq("EBAY_TRACKING_UPLOAD_FAILED"),
                Mockito.anyString(),
                Mockito.eq("SYSTEM"),
                Mockito.anyString()
        );
        Mockito.verify(ebayOrderClient, Mockito.never()).checkTrackingUploaded(Mockito.anyString());
    }
}
