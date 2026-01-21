package com.example.cbs_mvp.ebay;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

@Data
@Component
@ConfigurationProperties(prefix = "ebay")
public class EbayOAuthConfig {

    private String clientId;
    private String clientSecret;
    private String ruName;
    private boolean sandbox = true;
    private String apiBase = "https://api.sandbox.ebay.com";
    private String authBase = "https://auth.sandbox.ebay.com";

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
}
