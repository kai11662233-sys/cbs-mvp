package com.example.cbs_mvp.ebay;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import lombok.Data;

@Data
@Component
@ConfigurationProperties(prefix = "ebay")
public class EbayOAuthConfig {

    private static final Logger log = LoggerFactory.getLogger(EbayOAuthConfig.class);

    private String clientId;
    private String clientSecret;
    private String ruName;
    private boolean sandbox = true;
    private String apiBase = "https://api.sandbox.ebay.com";
    private String authBase = "https://auth.sandbox.ebay.com";

    // eBay ビジネスポリシーID（Sandbox/Productionで異なる）
    private String fulfillmentPolicyId;
    private String paymentPolicyId;
    private String returnPolicyId;

    // Webhook署名検証キー
    private String webhookVerificationToken;

    @PostConstruct
    public void init() {
        if (fulfillmentPolicyId == null || fulfillmentPolicyId.isBlank()) {
            log.warn("======================================");
            log.warn("⚠️ WARNING: ebay.fulfillment-policy-id not configured!");
            log.warn("Using ruName as fallback (will fail in production).");
            log.warn("Set proper policy IDs in application.yml");
            log.warn("======================================");
        }
    }

    public String getApiBase() {
        if (sandbox) {
            return "https://api.sandbox.ebay.com";
        }
        return "https://api.ebay.com";
    }

    public String getAuthBase() {
        if (sandbox) {
            return "https://auth.sandbox.ebay.com";
        }
        return "https://auth.ebay.com";
    }

    public String getTokenEndpoint() {
        return getAuthBase() + "/identity/v1/oauth2/token";
    }

    public String getEffectiveFulfillmentPolicyId() {
        return (fulfillmentPolicyId != null && !fulfillmentPolicyId.isBlank())
                ? fulfillmentPolicyId
                : ruName;
    }

    public String getEffectivePaymentPolicyId() {
        return (paymentPolicyId != null && !paymentPolicyId.isBlank())
                ? paymentPolicyId
                : ruName;
    }

    public String getEffectiveReturnPolicyId() {
        return (returnPolicyId != null && !returnPolicyId.isBlank())
                ? returnPolicyId
                : ruName;
    }

    public boolean isWebhookVerificationEnabled() {
        return webhookVerificationToken != null && !webhookVerificationToken.isBlank();
    }
}
