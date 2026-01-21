package com.example.cbs_mvp.ebay;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import lombok.RequiredArgsConstructor;

@Component
@Profile("real")
@RequiredArgsConstructor
public class RealEbayClient implements EbayClient {

    private static final Logger log = LoggerFactory.getLogger(RealEbayClient.class);

    private final EbayOAuthConfig config;
    private final EbayOAuthService oauthService;
    private final WebClient.Builder webClientBuilder;

    @Override
    public void putInventoryItem(String sku, Map<String, Object> payload) {
        log.info("PUT inventory_item sku={}", sku);

        String url = config.getApiBase() + "/sell/inventory/v1/inventory_item/" + sku;

        try {
            webClient()
                    .put()
                    .uri(url)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + oauthService.getAccessToken())
                    .header("Content-Language", "en-US")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(buildInventoryPayload(payload))
                    .retrieve()
                    .toBodilessEntity()
                    .block();

            log.info("PUT inventory_item success sku={}", sku);

        } catch (WebClientResponseException e) {
            log.error("PUT inventory_item failed sku={} status={} body={}",
                    sku, e.getStatusCode(), e.getResponseBodyAsString());
            throw new EbayClientException(
                    "putInventoryItem failed: " + e.getStatusCode() + " - " + e.getResponseBodyAsString(),
                    false);
        } catch (Exception e) {
            log.error("PUT inventory_item error sku={}", sku, e);
            throw new EbayClientException("putInventoryItem error: " + e.getMessage(), false);
        }
    }

    @Override
    public String createOffer(String sku, Map<String, Object> payload) {
        log.info("POST offer sku={}", sku);

        String url = config.getApiBase() + "/sell/inventory/v1/offer";

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = webClient()
                    .post()
                    .uri(url)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + oauthService.getAccessToken())
                    .header("Content-Language", "en-US")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(buildOfferPayload(sku, payload))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response == null || !response.containsKey("offerId")) {
                throw new EbayClientException("createOffer: no offerId in response", true);
            }

            String offerId = (String) response.get("offerId");
            log.info("POST offer success sku={} offerId={}", sku, offerId);
            return offerId;

        } catch (WebClientResponseException e) {
            log.error("POST offer failed sku={} status={} body={}",
                    sku, e.getStatusCode(), e.getResponseBodyAsString());
            throw new EbayClientException(
                    "createOffer failed: " + e.getStatusCode() + " - " + e.getResponseBodyAsString(),
                    true);
        } catch (EbayClientException e) {
            throw e;
        } catch (Exception e) {
            log.error("POST offer error sku={}", sku, e);
            throw new EbayClientException("createOffer error: " + e.getMessage(), true);
        }
    }

    @Override
    public boolean checkOfferExists(String offerId) {
        if (offerId == null || offerId.isBlank()) {
            return false;
        }

        log.info("GET offer offerId={}", offerId);

        String url = config.getApiBase() + "/sell/inventory/v1/offer/" + offerId;

        try {
            webClient()
                    .get()
                    .uri(url)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + oauthService.getAccessToken())
                    .retrieve()
                    .toBodilessEntity()
                    .block();

            log.info("GET offer exists offerId={}", offerId);
            return true;

        } catch (WebClientResponseException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                log.info("GET offer not found offerId={}", offerId);
                return false;
            }
            log.warn("GET offer check failed offerId={} status={}", offerId, e.getStatusCode());
            return false; // エラー時はfalseを返して安全側に倒す
        } catch (Exception e) {
            log.warn("GET offer check error offerId={}", offerId, e);
            return false;
        }
    }

    private WebClient webClient() {
        return webClientBuilder
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    private Map<String, Object> buildInventoryPayload(Map<String, Object> input) {
        // eBay Inventory API形式に変換
        return Map.of(
                "availability", Map.of(
                        "shipToLocationAvailability", Map.of(
                                "quantity", 1)),
                "condition", "USED_EXCELLENT",
                "product", Map.of(
                        "title", "Item " + input.get("sku"),
                        "description", "Product for SKU " + input.get("sku")));
    }

    private Map<String, Object> buildOfferPayload(String sku, Map<String, Object> input) {
        Object priceObj = input.get("offerPriceUsd");
        String priceStr = priceObj != null ? priceObj.toString() : "10.00";

        return Map.of(
                "sku", sku,
                "marketplaceId", "EBAY_US",
                "format", "FIXED_PRICE",
                "listingDescription", "Offer for " + sku,
                "pricingSummary", Map.of(
                        "price", Map.of(
                                "value", priceStr,
                                "currency", "USD")),
                "listingPolicies", Map.of(
                        "fulfillmentPolicyId", config.getEffectiveFulfillmentPolicyId(),
                        "paymentPolicyId", config.getEffectivePaymentPolicyId(),
                        "returnPolicyId", config.getEffectiveReturnPolicyId()));
    }
}
