package com.example.cbs_mvp.ebay;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/ebay")
@RequiredArgsConstructor
public class EbayWebhookController {

    private static final Logger log = LoggerFactory.getLogger(EbayWebhookController.class);

    private final EbayOAuthConfig config;
    private final ObjectMapper objectMapper;

    /**
     * eBay通知受信エンドポイント
     * 
     * eBay Notification APIからの通知を処理:
     * - ITEM_SOLD: 商品が売れた通知
     * - ORDER_CREATED: 注文作成通知
     */
    @PostMapping("/webhook")
    public ResponseEntity<?> handleWebhook(
            @RequestHeader(value = "X-EBAY-SIGNATURE", required = false) String signature,
            @RequestBody String rawPayload) {

        // ⚠️ セキュリティ: 生ペイロードをログに出さない
        log.info("eBay webhook received, size={} bytes", rawPayload != null ? rawPayload.length() : 0);

        // 署名検証
        if (config.isWebhookVerificationEnabled()) {
            if (!verifySignature(signature, rawPayload)) {
                log.warn("⚠️ Webhook signature verification FAILED - possible spoofing attempt");
                return ResponseEntity.status(401)
                        .body(Map.of("error", "signature verification failed"));
            }
            log.info("Webhook signature verified successfully");
        } else {
            log.warn("⚠️ Webhook signature verification DISABLED - configure ebay.webhook-verification-token!");
        }

        try {
            // JSONパース（署名検証後）
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = parseJson(rawPayload);

            String notificationType = (String) payload.get("notificationType");

            if (notificationType == null) {
                // Notification API v1形式
                notificationType = extractNotificationTypeV1(payload);
            }

            if (notificationType == null) {
                log.warn("Unknown webhook format");
                return ResponseEntity.ok(Map.of("status", "ignored", "reason", "unknown format"));
            }

            switch (notificationType) {
                case "MARKETPLACE_ACCOUNT_DELETION":
                    // eBayのチャレンジリクエスト用（エンドポイント検証）
                    return handleChallengeRequest(payload);

                case "ITEM_SOLD":
                case "ORDER_CREATED":
                case "ORDER.CREATED":
                    return handleOrderCreated(payload);

                case "ITEM_SHIPPED":
                case "ORDER_SHIPMENT":
                    log.info("Shipment notification received (no action needed)");
                    return ResponseEntity.ok(Map.of("status", "acknowledged"));

                default:
                    log.info("Unhandled notification type: {}", notificationType);
                    return ResponseEntity.ok(Map.of("status", "ignored", "type", notificationType));
            }

        } catch (Exception e) {
            log.error("Webhook processing failed", e);
            return ResponseEntity.ok(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    /**
     * eBay署名検証
     * 
     * eBay Notification APIの署名形式:
     * X-EBAY-SIGNATUREはBase64エンコードされたJSONヘッダーで、
     * 内部に"signature"フィールドを含む場合がある。
     * 
     * また、単純なHMAC署名の場合はBase64またはhexエンコード。
     * 両方の形式に対応。
     * 
     * https://developer.ebay.com/api-docs/sell/notification/overview.html
     */
    private boolean verifySignature(String signatureHeader, String payload) {
        if (signatureHeader == null || signatureHeader.isBlank()) {
            log.warn("Missing X-EBAY-SIGNATURE header");
            return false;
        }

        try {
            String token = config.getWebhookVerificationToken();
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(
                    token.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);

            byte[] expectedHash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));

            // 署名を抽出（Base64 JSON形式の場合は内部のsignatureフィールドを使用）
            String actualSignature = extractSignature(signatureHeader);

            // Base64形式で比較（eBayの標準形式）
            String expectedBase64 = java.util.Base64.getEncoder().encodeToString(expectedHash);
            if (constantTimeEquals(expectedBase64, actualSignature)) {
                return true;
            }

            // Hex形式でも比較（互換性のため）
            String expectedHex = bytesToHex(expectedHash);
            if (constantTimeEquals(expectedHex.toLowerCase(), actualSignature.toLowerCase())) {
                return true;
            }

            log.warn("Signature mismatch");
            return false;

        } catch (Exception e) {
            log.error("Signature verification error", e);
            return false;
        }
    }

    /**
     * X-EBAY-SIGNATUREから実際の署名を抽出
     * Base64 JSON形式の場合は内部の"signature"フィールドを返す
     */
    private String extractSignature(String signatureHeader) {
        try {
            // Base64デコードしてJSONとしてパース
            String decoded = new String(
                    java.util.Base64.getDecoder().decode(signatureHeader),
                    StandardCharsets.UTF_8);

            @SuppressWarnings("unchecked")
            Map<String, Object> sigJson = objectMapper.readValue(decoded, Map.class);

            if (sigJson.containsKey("signature")) {
                return (String) sigJson.get("signature");
            }
        } catch (Exception e) {
            // Base64 JSONではない場合は元の値をそのまま使用
        }
        return signatureHeader;
    }

    /**
     * 定数時間比較（タイミング攻撃対策）
     */
    private boolean constantTimeEquals(String a, String b) {
        byte[] aBytes = a.getBytes(StandardCharsets.UTF_8);
        byte[] bBytes = b.getBytes(StandardCharsets.UTF_8);
        return java.security.MessageDigest.isEqual(aBytes, bBytes);
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJson(String json) {
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            throw new RuntimeException("JSON parse error", e);
        }
    }

    private ResponseEntity<?> handleChallengeRequest(Map<String, Object> payload) {
        String challengeCode = (String) payload.get("challengeCode");
        if (challengeCode != null) {
            log.info("Challenge request received");
            return ResponseEntity.ok(Map.of("challengeResponse", challengeCode));
        }
        return ResponseEntity.ok(Map.of("status", "acknowledged"));
    }

    private ResponseEntity<?> handleOrderCreated(Map<String, Object> payload) {
        log.info("Processing order created notification");

        try {
            // ペイロードから注文情報を抽出
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) payload.get("data");
            if (data == null) {
                data = payload; // v1形式の場合
            }

            String orderId = extractOrderId(data);
            if (orderId == null) {
                log.warn("No order ID found in webhook payload");
                return ResponseEntity.ok(Map.of("status", "ignored", "reason", "no orderId"));
            }

            // ログにはマスキング
            log.info("Order notification received: orderId={}", maskOrderId(orderId));
            log.info("Action required: Call POST /orders/sold with the order ID");

            return ResponseEntity.ok(Map.of(
                    "status", "received",
                    "orderId", orderId,
                    "action", "manual /orders/sold call required"));

        } catch (Exception e) {
            log.error("Error processing order notification", e);
            return ResponseEntity.ok(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    private String maskOrderId(String orderId) {
        if (orderId == null || orderId.length() <= 8) {
            return "***";
        }
        return orderId.substring(0, 4) + "..." + orderId.substring(orderId.length() - 4);
    }

    private String extractNotificationTypeV1(Map<String, Object> payload) {
        // v1形式の通知タイプ抽出
        if (payload.containsKey("metadata")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> metadata = (Map<String, Object>) payload.get("metadata");
            if (metadata != null) {
                return (String) metadata.get("topic");
            }
        }
        return null;
    }

    private String extractOrderId(Map<String, Object> data) {
        // 様々な形式に対応
        if (data.containsKey("orderId")) {
            return (String) data.get("orderId");
        }
        if (data.containsKey("OrderID")) {
            return (String) data.get("OrderID");
        }
        if (data.containsKey("order")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> order = (Map<String, Object>) data.get("order");
            if (order != null) {
                return (String) order.get("orderId");
            }
        }
        return null;
    }
}
