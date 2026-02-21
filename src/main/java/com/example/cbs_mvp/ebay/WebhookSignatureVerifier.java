package com.example.cbs_mvp.ebay;

import java.nio.charset.StandardCharsets;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Webhook署名検証ユーティリティ。
 * HMAC-SHA256 でリクエストボディの整合性を検証する。
 */
@Component
public class WebhookSignatureVerifier {

    private static final Logger log = LoggerFactory.getLogger(WebhookSignatureVerifier.class);
    private static final String ALGORITHM = "HmacSHA256";

    /**
     * HMAC-SHA256署名を検証する。
     *
     * @param payload   リクエストボディの生文字列
     * @param signature X-Ebay-Signature ヘッダーの値
     * @param secret    共有シークレット
     * @return 署名が一致すれば true
     */
    public boolean verify(String payload, String signature, String secret) {
        if (payload == null || signature == null || secret == null) {
            return false;
        }
        try {
            // 正規化: sha256= プレフィックスの除去
            String targetSignature = signature.trim();
            if (targetSignature.toLowerCase().startsWith("sha256=")) {
                targetSignature = targetSignature.substring(7);
            }

            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            byte[] computedHash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));

            // 署名をバイト配列にデコードして比較（定数時間比較）
            byte[] providedHash = hexToBytes(targetSignature);
            if (providedHash == null) {
                return false;
            }

            return java.security.MessageDigest.isEqual(computedHash, providedHash);
        } catch (Exception e) {
            log.error("HMAC署名検証中にエラーが発生しました", e);
            return false;
        }
    }

    private static byte[] hexToBytes(String hex) {
        if (hex == null || hex.length() % 2 != 0) {
            return null;
        }
        byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < hex.length(); i += 2) {
            int hi = Character.digit(hex.charAt(i), 16);
            int lo = Character.digit(hex.charAt(i + 1), 16);
            if (hi < 0 || lo < 0) {
                return null;
            }
            bytes[i / 2] = (byte) ((hi << 4) + lo);
        }
        return bytes;
    }
}
