package com.example.cbs_mvp.ebay;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import lombok.RequiredArgsConstructor;

@Component
@Profile("real")
@RequiredArgsConstructor
public class RealEbayOrderClient implements EbayOrderClient {

    private static final Logger log = LoggerFactory.getLogger(RealEbayOrderClient.class);

    private final EbayOAuthConfig config;
    private final EbayOAuthService oauthService;
    private final WebClient.Builder webClientBuilder;

    @Override
    public void uploadTracking(String ebayOrderKey, String carrier, String tracking) {
        log.info("POST shipping_fulfillment orderKey={} carrier={} tracking={}",
                ebayOrderKey, carrier, tracking);

        String url = config.getApiBase() + "/sell/fulfillment/v1/order/" + ebayOrderKey + "/shipping_fulfillment";

        try {
            Map<String, Object> payload = Map.of(
                    "lineItems", List.of(
                            Map.of(
                                    "lineItemId", "0", // 全ラインアイテムを対象
                                    "quantity", 1)),
                    "shippedDate", java.time.Instant.now().toString(),
                    "shippingCarrierCode", mapCarrierCode(carrier),
                    "trackingNumber", tracking);

            webClient()
                    .post()
                    .uri(url)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + oauthService.getAccessToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(payload)
                    .retrieve()
                    .toBodilessEntity()
                    .block();

            log.info("POST shipping_fulfillment success orderKey={}", ebayOrderKey);

        } catch (WebClientResponseException e) {
            log.error("POST shipping_fulfillment failed orderKey={} status={} body={}",
                    ebayOrderKey, e.getStatusCode(), e.getResponseBodyAsString());

            boolean isRetryable = e.getStatusCode().is5xxServerError()
                    || e.getStatusCode().value() == 429;

            throw new EbayOrderClientException(
                    "uploadTracking failed: " + e.getStatusCode() + " - " + e.getResponseBodyAsString(),
                    isRetryable);
        } catch (Exception e) {
            log.error("POST shipping_fulfillment error orderKey={}", ebayOrderKey, e);
            throw new EbayOrderClientException("uploadTracking error: " + e.getMessage(), true);
        }
    }

    @Override
    public boolean checkTrackingUploaded(String ebayOrderKey) {
        if (ebayOrderKey == null || ebayOrderKey.isBlank()) {
            return false;
        }

        log.info("GET shipping_fulfillment orderKey={}", ebayOrderKey);

        String url = config.getApiBase() + "/sell/fulfillment/v1/order/" + ebayOrderKey + "/shipping_fulfillment";

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = webClient()
                    .get()
                    .uri(url)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + oauthService.getAccessToken())
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response == null) {
                return false;
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> fulfillments = (List<Map<String, Object>>) response.get("fulfillments");
            boolean hasTracking = fulfillments != null && !fulfillments.isEmpty();

            log.info("GET shipping_fulfillment orderKey={} hasTracking={}", ebayOrderKey, hasTracking);
            return hasTracking;

        } catch (WebClientResponseException e) {
            log.warn("GET shipping_fulfillment check failed orderKey={} status={}",
                    ebayOrderKey, e.getStatusCode());
            return false;
        } catch (Exception e) {
            log.warn("GET shipping_fulfillment check error orderKey={}", ebayOrderKey, e);
            return false;
        }
    }

    @Override
    public Map<String, Object> getOrder(String ebayOrderKey) {
        log.info("GET order orderKey={}", ebayOrderKey);
        String url = config.getApiBase() + "/sell/fulfillment/v1/order/" + ebayOrderKey;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = webClient()
                    .get()
                    .uri(url)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + oauthService.getAccessToken())
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
            return response;
        } catch (Exception e) {
            log.error("GET order failed orderKey={}", ebayOrderKey, e);
            throw new EbayOrderClientException("getOrder failed: " + e.getMessage(), true);
        }
    }

    private WebClient webClient() {
        return webClientBuilder
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    private String mapCarrierCode(String carrier) {
        if (carrier == null)
            return "OTHER";

        String upper = carrier.toUpperCase();
        if (upper.contains("JAPANPOST") || upper.contains("EMS")) {
            return "Japan_Post";
        } else if (upper.contains("DHL")) {
            return "DHL";
        } else if (upper.contains("FEDEX")) {
            return "FedEx";
        } else if (upper.contains("UPS")) {
            return "UPS";
        } else if (upper.contains("YAMATO")) {
            return "Yamato";
        }
        return "OTHER";
    }
}
