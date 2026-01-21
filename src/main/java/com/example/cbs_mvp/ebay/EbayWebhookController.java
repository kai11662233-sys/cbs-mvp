package com.example.cbs_mvp.ebay;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.cbs_mvp.service.OrderImportService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/ebay")
@RequiredArgsConstructor
public class EbayWebhookController {

    private static final Logger log = LoggerFactory.getLogger(EbayWebhookController.class);

    private final OrderImportService orderImportService;

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
            @RequestBody Map<String, Object> payload) {
        log.info("eBay webhook received: {}", payload);

        // TODO: 本番環境では署名検証を実装
        // verifySignature(signature, payload);

        try {
            String notificationType = (String) payload.get("notificationType");

            if (notificationType == null) {
                // Notification API v1形式
                notificationType = extractNotificationTypeV1(payload);
            }

            if (notificationType == null) {
                log.warn("Unknown webhook format: {}", payload);
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

    private ResponseEntity<?> handleChallengeRequest(Map<String, Object> payload) {
        String challengeCode = (String) payload.get("challengeCode");
        if (challengeCode != null) {
            log.info("Challenge request received: {}", challengeCode);
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

            // TODO: eBay APIから注文詳細を取得して処理
            // 現在は手動で /orders/sold を呼び出す運用
            log.info("Order notification received: orderId={}", orderId);
            log.info("Action required: Call POST /orders/sold with orderId={}", orderId);

            return ResponseEntity.ok(Map.of(
                    "status", "received",
                    "orderId", orderId,
                    "action", "manual /orders/sold call required"));

        } catch (Exception e) {
            log.error("Error processing order notification", e);
            return ResponseEntity.ok(Map.of("status", "error", "message", e.getMessage()));
        }
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
