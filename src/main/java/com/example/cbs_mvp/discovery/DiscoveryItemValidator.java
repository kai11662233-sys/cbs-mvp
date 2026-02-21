package com.example.cbs_mvp.discovery;

import java.math.BigDecimal;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.example.cbs_mvp.dto.discovery.DiscoverySeed;

/**
 * DiscoveryItem 取り込み前のバリデーション＋正規化ユーティリティ。
 *
 * <ul>
 * <li>必須項目チェック（URL, 価格, タイトル）</li>
 * <li>異常値チェック（価格0以下, 重量負数）</li>
 * <li>NGキーワード検出</li>
 * <li>URL正規化（クエリ除去, http→https, 末尾/統一）</li>
 * </ul>
 */
@Component
public class DiscoveryItemValidator {

    // --- NGキーワード（タイトルに含まれていれば即NG） ---
    private static final Set<String> NG_KEYWORDS = Set.of(
            "ジャンク", "現状渡し", "現状品", "未確認", "動作未確認",
            "部品取り", "通電のみ", "故障", "壊れ", "不動",
            "返品不可", "ノークレーム", "ノーリターン");

    // タイトルに含まれる文字化けパターン（連続する?や□）
    private static final Pattern GARBLED_PATTERN = Pattern.compile("[\\?\\ufffd\\u25a1]{3,}");

    // --- バリデーション ---

    /**
     * Seed のバリデーションを実行し、結果を返す。
     */
    public ValidationResult validate(DiscoverySeed seed) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // 必須項目
        if (seed.sourceUrl() == null || seed.sourceUrl().isBlank()) {
            errors.add("sourceUrl は必須です");
        }
        if (seed.priceYen() == null) {
            errors.add("priceYen は必須です");
        }
        if (seed.title() == null || seed.title().isBlank()) {
            errors.add("title は必須です");
        }

        // 異常値
        if (seed.priceYen() != null && seed.priceYen().compareTo(BigDecimal.ZERO) <= 0) {
            errors.add("priceYen が0以下です: " + seed.priceYen());
        }
        if (seed.weightKg() != null && seed.weightKg().compareTo(BigDecimal.ZERO) < 0) {
            errors.add("weightKg が負数です: " + seed.weightKg());
        }

        // 文字化けチェック
        if (seed.title() != null && GARBLED_PATTERN.matcher(seed.title()).find()) {
            warnings.add("タイトルに文字化けの可能性があります");
        }

        boolean ok = errors.isEmpty();
        return new ValidationResult(ok, errors, warnings);
    }

    /**
     * タイトルにNGキーワードが含まれているかチェック。
     */
    public boolean containsNgKeyword(String title) {
        if (title == null)
            return false;
        String lower = title.toLowerCase();
        return NG_KEYWORDS.stream().anyMatch(lower::contains);
    }

    /**
     * NGキーワードのリストを返す（riskFlags用）。
     */
    public List<String> findNgKeywords(String title) {
        if (title == null)
            return List.of();
        String lower = title.toLowerCase();
        return NG_KEYWORDS.stream()
                .filter(lower::contains)
                .toList();
    }

    // --- URL正規化 ---

    /**
     * URLを正規化する:
     * <ol>
     * <li>http → https に統一</li>
     * <li>クエリパラメータ除去</li>
     * <li>フラグメント除去</li>
     * <li>末尾スラッシュ除去</li>
     * <li>小文字化（ホスト部分）</li>
     * </ol>
     */
    public String normalizeUrl(String url) {
        if (url == null || url.isBlank())
            return url;
        try {
            String trimmed = url.trim();

            // http → https
            if (trimmed.startsWith("http://")) {
                trimmed = "https://" + trimmed.substring(7);
            }

            URI uri = URI.create(trimmed);
            // ホスト小文字化 + パスのみ残す（クエリ・フラグメント除去）
            String host = uri.getHost() != null ? uri.getHost().toLowerCase() : "";
            String path = uri.getPath() != null ? uri.getPath() : "";
            String scheme = uri.getScheme() != null ? uri.getScheme() : "https";
            int port = uri.getPort();

            // 末尾スラッシュ除去
            if (path.endsWith("/") && path.length() > 1) {
                path = path.substring(0, path.length() - 1);
            }

            String normalized = scheme + "://" + host;
            if (port > 0 && port != 443 && port != 80) {
                normalized += ":" + port;
            }
            normalized += path;

            return normalized;
        } catch (Exception e) {
            // パースできなければそのまま返す
            return url.trim();
        }
    }

    // --- 結果クラス ---

    public record ValidationResult(
            boolean ok,
            List<String> errors,
            List<String> warnings) {
    }
}
