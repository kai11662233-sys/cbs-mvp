package com.example.cbs_mvp.ebay;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.example.cbs_mvp.entity.EbayDraft;
import com.example.cbs_mvp.entity.Order;
import com.example.cbs_mvp.fx.FxRateService;
import com.example.cbs_mvp.repo.EbayDraftRepository;
import com.example.cbs_mvp.repo.OrderRepository;
import com.example.cbs_mvp.service.OrderImportService;
import com.example.cbs_mvp.service.OrderImportService.SoldImportCommand;
import com.fasterxml.jackson.databind.ObjectMapper;

class EbayWebhookControllerTest {

        private MockMvc mvc;

        @Mock
        private EbayOrderClient ebayOrderClient;

        @Mock
        private OrderRepository orderRepository;

        @Mock
        private EbayDraftRepository draftRepository;

        @Mock
        private OrderImportService orderImportService;

        @Mock
        private FxRateService fxRateService;

        @Mock
        private WebhookSignatureVerifier signatureVerifier;

        @InjectMocks
        private EbayWebhookController controller;

        private ObjectMapper objectMapper = new ObjectMapper();

        @BeforeEach
        void setup() {
                MockitoAnnotations.openMocks(this);
                mvc = MockMvcBuilders.standaloneSetup(controller).build();
        }

        @Test
        void receiveWebhook_shouldImportOrder() throws Exception {
                String orderId = "11-222-333";
                String sku = "TEST-SKU-001";
                Long draftId = 100L;
                BigDecimal soldPrice = new BigDecimal("50.00");
                BigDecimal fxRate = new BigDecimal("150.00");

                // Mock: Order not exists
                when(orderRepository.findByEbayOrderKey(orderId)).thenReturn(Optional.empty());

                // Mock: Order details
                Map<String, Object> orderDetails = Map.of(
                                "orderId", orderId,
                                "lineItems", List.of(Map.of("sku", sku)),
                                "pricingSummary", Map.of("total", Map.of("value", soldPrice.toString())));
                when(ebayOrderClient.getOrder(orderId)).thenReturn(orderDetails);

                // Mock: Draft exists
                EbayDraft draft = new EbayDraft();
                draft.setDraftId(draftId);
                draft.setSku(sku);
                when(draftRepository.findBySku(sku)).thenReturn(Optional.of(draft));

                // Mock: FxRate
                when(fxRateService.getCurrentRate())
                                .thenReturn(new FxRateService.FxRateResult(fxRate, Instant.now(), null));

                // Payload
                Map<String, Object> payload = Map.of(
                                "notification", Map.of(
                                                "data", Map.of("orderId", orderId)));

                mvc.perform(post("/ebay/webhook")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(payload)))
                                .andExpect(status().isOk());

                verify(orderImportService).importSold(any(SoldImportCommand.class));
        }

        @Test
        void receiveWebhook_shouldSkip_ifAlreadyExists() throws Exception {
                String orderId = "11-222-333";

                // Mock: Order exists
                when(orderRepository.findByEbayOrderKey(orderId)).thenReturn(Optional.of(new Order()));

                // Payload
                Map<String, Object> payload = Map.of("orderId", orderId);

                mvc.perform(post("/ebay/webhook")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(payload)))
                                .andExpect(status().isOk());

                verify(ebayOrderClient, never()).getOrder(any());
                verify(orderImportService, never()).importSold(any());
        }

        @Test
        void receiveWebhook_shouldSkip_ifDraftNotFound() throws Exception {
                String orderId = "11-222-333";
                String sku = "UNKNOWN-SKU";

                when(orderRepository.findByEbayOrderKey(orderId)).thenReturn(Optional.empty());

                Map<String, Object> orderDetails = Map.of(
                                "orderId", orderId,
                                "lineItems", List.of(Map.of("sku", sku)));
                when(ebayOrderClient.getOrder(orderId)).thenReturn(orderDetails);
                when(draftRepository.findBySku(sku)).thenReturn(Optional.empty());

                Map<String, Object> payload = Map.of("orderId", orderId);

                mvc.perform(post("/ebay/webhook")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(payload)))
                                .andExpect(status().isOk()); // Returns 200 but logs error

                verify(orderImportService, never()).importSold(any());
        }

        @Test
        void receiveWebhook_shouldReturn500_onApiError() throws Exception {
                String orderId = "11-222-333";

                when(orderRepository.findByEbayOrderKey(orderId)).thenReturn(Optional.empty());
                when(ebayOrderClient.getOrder(orderId)).thenThrow(new RuntimeException("API Error"));

                Map<String, Object> payload = Map.of("orderId", orderId);

                mvc.perform(post("/ebay/webhook")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(payload)))
                                .andExpect(status().isInternalServerError());
        }
}
