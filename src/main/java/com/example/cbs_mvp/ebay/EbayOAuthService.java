package com.example.cbs_mvp.ebay;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EbayOAuthService {

    private static final Logger log = LoggerFactory.getLogger(EbayOAuthService.class);
    private static final long TOKEN_REFRESH_BUFFER_SECONDS = 300; // 5分前にリフレッシュ

    private final EbayOAuthConfig config;
    private final WebClient.Builder webClientBuilder;

    private String cachedAccessToken;
    private Instant tokenExpiresAt;
    private final ReentrantLock tokenLock = new ReentrantLock();

    /**
     * 有効なアクセストークンを取得する。
     * キャッシュがあり有効期限内ならそれを返す。
     * 期限切れ or なければ新規取得。
     */
    public String getAccessToken() {
        tokenLock.lock();
        try {
            if (cachedAccessToken != null && tokenExpiresAt != null) {
                if (Instant.now().plusSeconds(TOKEN_REFRESH_BUFFER_SECONDS).isBefore(tokenExpiresAt)) {
                    return cachedAccessToken;
                }
            }
            return refreshToken();
        } finally {
            tokenLock.unlock();
        }
    }

    /**
     * トークンを強制リフレッシュ。
     */
    public String refreshToken() {
        tokenLock.lock();
        try {
            log.info("Refreshing eBay OAuth token...");

            if (config.getClientId() == null || config.getClientId().isBlank()) {
                throw new EbayClientException("eBay clientId is not configured", false);
            }
            if (config.getClientSecret() == null || config.getClientSecret().isBlank()) {
                throw new EbayClientException("eBay clientSecret is not configured", false);
            }

            String credentials = config.getClientId() + ":" + config.getClientSecret();
            String encodedCredentials = Base64.getEncoder()
                    .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));

            MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
            formData.add("grant_type", "client_credentials");
            formData.add("scope", "https://api.ebay.com/oauth/api_scope "
                    + "https://api.ebay.com/oauth/api_scope/sell.inventory "
                    + "https://api.ebay.com/oauth/api_scope/sell.fulfillment");

            WebClient client = webClientBuilder.build();

            @SuppressWarnings("unchecked")
            Map<String, Object> response = client.post()
                    .uri(config.getTokenEndpoint())
                    .header(HttpHeaders.AUTHORIZATION, "Basic " + encodedCredentials)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters.fromFormData(formData))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response == null || !response.containsKey("access_token")) {
                throw new EbayClientException("Failed to obtain eBay OAuth token: empty response", false);
            }

            cachedAccessToken = (String) response.get("access_token");
            int expiresIn = (Integer) response.getOrDefault("expires_in", 7200);
            tokenExpiresAt = Instant.now().plusSeconds(expiresIn);

            log.info("eBay OAuth token refreshed. Expires at: {}", tokenExpiresAt);
            return cachedAccessToken;

        } catch (EbayClientException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to refresh eBay OAuth token", e);
            throw new EbayClientException("OAuth token refresh failed: " + e.getMessage(), false);
        } finally {
            tokenLock.unlock();
        }
    }

    /**
     * 現在のトークン状態を確認（デバッグ用）
     */
    public Map<String, Object> getTokenStatus() {
        return Map.of(
                "hasToken", cachedAccessToken != null,
                "expiresAt", tokenExpiresAt != null ? tokenExpiresAt.toString() : "none",
                "isValid", isTokenValid());
    }

    private boolean isTokenValid() {
        return cachedAccessToken != null && tokenExpiresAt != null
                && Instant.now().plusSeconds(TOKEN_REFRESH_BUFFER_SECONDS).isBefore(tokenExpiresAt);
    }
}
