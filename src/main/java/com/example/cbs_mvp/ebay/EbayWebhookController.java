package com.example.cbs_mvp.ebay;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.cbs_mvp.entity.EbayDraft;
import com.example.cbs_mvp.repo.EbayDraftRepository;
import com.example.cbs_mvp.repo.OrderRepository;
import com.example.cbs_mvp.fx.FxRateService;
import com.example.cbs_mvp.service.OrderImportService;
import com.example.cbs_mvp.service.OrderImportService.SoldImportCommand;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

@RestController
@RequestMapping("/ebay/webhook")
public class EbayWebhookController {

    private static final Logger log = LoggerFactory.getLogger(EbayWebhookController.class);

    private final EbayOrderClient ebayOrderClient;
    private final OrderRepository orderRepository;
    private final EbayDraftRepository draftRepository;
    private final OrderImportService orderImportService;
    private final FxRateService fxRateService;
    private final WebhookSignatureVerifier signatureVerifier;
    private final ObjectMapper objectMapper;

    @Value("${EBAY_WEBHOOK_SECRET:}")
    private String webhookSecret;

    @Value("${spring.profiles.active:stub}")
    private String activeProfile;

    public EbayWebhookController(
            EbayOrderClient ebayOrderClient,
            OrderRepository orderRepository,
            EbayDraftRepository draftRepository,
            OrderImportService orderImportService,
            FxRateService fxRateService,
            WebhookSignatureVerifier signatureVerifier,
            ObjectMapper objectMapper) {
        this.ebayOrderClient = ebayOrderClient;
        this.orderRepository = orderRepository;
        this.draftRepository = draftRepository;
        this.orderImportService = orderImportService;
        this.fxRateService = fxRateService;
        this.signatureVerifier = signatureVerifier;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<?> receiveWebhook(
            @RequestHeader(value = "X-Ebay-Signature", required = false) String signature,
            @RequestBody String rawBody) {

        // 1. 署名検証
        boolean isSecretConfigured = webhookSecret != null && !webhookSecret.isBlank();
        boolean isProd = "real".equalsIgnoreCase(activeProfile) || "prod".equalsIgnoreCase(activeProfile);

        if (!isSecretConfigured) {
            if (isProd) {
                log.error("EBAY_WEBHOOK_SECRET が未設定です。本番環境ではWebhookを処理できません。");
                return ResponseEntity.status(503).body("webhook secret not configured");
            }
            log.warn("⚠️ EBAY_WEBHOOK_SECRET 未設定。署名検証をスキップします（開発モード）。");
        } else {
            if (signature == null || signature.isBlank()) {
                log.warn("Webhook署名ヘッダーがありません。リクエストを拒否します。");
                return ResponseEntity.status(401).body("missing signature");
            }
            if (!signatureVerifier.verify(rawBody, signature, webhookSecret)) {
                log.warn("Webhook署名が不正です。リクエストを拒否します。");
                return ResponseEntity.status(401).body("invalid signature");
            }
            log.debug("Webhook署名検証OK");
        }

        Map<String, Object> payload;
        try {
            payload = objectMapper.readValue(rawBody, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            log.error("Webhook payload のパースに失敗しました", e);
            return ResponseEntity.badRequest().body("invalid JSON");
        }

        // 2. PayloadからorderId抽出
        // eBay Notification payload構造:
        // { "metadata": {...}, "notification": { "data": { "orderId": "..." },
        // "eventDate": "...", "publishDate": "...", "triggerDate": "..." }, "summary":
        // ... }
        // 簡易的に直下のorderIdも見れるようにしておく（テスト用）
        String orderId = extractOrderId(payload);
        if (orderId == null) {
            log.warn("Callback payload missing orderId");
            return ResponseEntity.badRequest().body("missing orderId");
        }

        log.info("Webhook received for orderId={}", orderId);

        // 3. 冪等性チェック
        if (orderRepository.findByEbayOrderKey(orderId).isPresent()) {
            log.info("Order {} already exists. Skipping.", orderId);
            return ResponseEntity.ok().build();
        }

        // 4. 詳細取得
        Map<String, Object> orderDetails;
        try {
            orderDetails = ebayOrderClient.getOrder(orderId);
        } catch (Exception e) {
            log.error("Failed to fetch order details for {}", orderId, e);
            return ResponseEntity.internalServerError().build(); // 500 (Retryable)
        }

        // 5. SKU抽出 & Draft特定
        String sku = extractSku(orderDetails);
        if (sku == null) {
            log.error("SKU not found in order details for {}", orderId);
            // 処理不能だが、Webhookとしては受信完了とする（リトライさせない）
            return ResponseEntity.ok().build();
        }

        Optional<EbayDraft> draftOpt = draftRepository.findBySku(sku);
        if (draftOpt.isEmpty()) {
            log.warn("Draft not found for SKU: {} (Order: {})", sku, orderId);
            // 要件B: 200で受理して監視ログに残す
            return ResponseEntity.ok().build();
        }
        EbayDraft draft = draftOpt.get();

        // 6. Import Sold
        BigDecimal fxRate = BigDecimal.valueOf(150.0);
        var rateResult = fxRateService.getCurrentRate();
        if (rateResult.isSuccess() && rateResult.rate() != null) {
            fxRate = rateResult.rate();
        }

        BigDecimal soldPrice = extractSoldPrice(orderDetails);

        try {
            orderImportService.importSold(new SoldImportCommand(
                    orderId,
                    draft.getDraftId(),
                    soldPrice,
                    fxRate));
            log.info("Order imported successfully: {}", orderId);
        } catch (Exception e) {
            log.error("Failed to import order {}", orderId, e);
            return ResponseEntity.internalServerError().build();
        }

        return ResponseEntity.ok().build();
    }

    @SuppressWarnings("unchecked")
    private String extractOrderId(Map<String, Object> payload) {
        if (payload.containsKey("orderId")) {
            return (String) payload.get("orderId");
        }
        // Notification structure
        if (payload.containsKey("notification")) {
            Map<String, Object> notif = (Map<String, Object>) payload.get("notification");
            if (notif != null && notif.containsKey("data")) {
                Map<String, Object> data = (Map<String, Object>) notif.get("data");
                if (data != null && data.containsKey("orderId")) {
                    return (String) data.get("orderId");
                }
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private String extractSku(Map<String, Object> orderDetails) {
        // lineItems -> [0] -> sku
        if (orderDetails.containsKey("lineItems")) {
            List<Map<String, Object>> items = (List<Map<String, Object>>) orderDetails.get("lineItems");
            if (items != null && !items.isEmpty()) {
                Map<String, Object> firstItem = items.get(0);
                return (String) firstItem.get("sku");
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private BigDecimal extractSoldPrice(Map<String, Object> orderDetails) {
        // pricingSummary -> total -> value
        if (orderDetails.containsKey("pricingSummary")) {
            Map<String, Object> pricing = (Map<String, Object>) orderDetails.get("pricingSummary");
            if (pricing != null && pricing.containsKey("total")) {
                Map<String, Object> total = (Map<String, Object>) pricing.get("total");
                if (total != null && total.containsKey("value")) {
                    return new BigDecimal((String) total.get("value"));
                }
            }
        }
        // Fallback: lineItems sum?
        return BigDecimal.ZERO;
    }
}
