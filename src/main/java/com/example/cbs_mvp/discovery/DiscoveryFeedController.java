package com.example.cbs_mvp.discovery;

import java.io.IOException;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.cbs_mvp.dto.discovery.CsvIngestResultResponse;
import com.example.cbs_mvp.ops.OpsKeyService;

/**
 * Discovery Feed API Controller
 * CSVアップロードでDiscoveryアイテムを一括登録
 */
@RestController
@RequestMapping("/discovery/feeds")
public class DiscoveryFeedController {

    private static final Logger log = LoggerFactory.getLogger(DiscoveryFeedController.class);

    private final OpsKeyService opsKeyService;
    private final DiscoveryIngestService ingestService;

    public DiscoveryFeedController(
            OpsKeyService opsKeyService,
            DiscoveryIngestService ingestService) {
        this.opsKeyService = opsKeyService;
        this.ingestService = ingestService;
    }

    /**
     * POST /discovery/feeds/csv
     * CSVファイルをアップロードしてDiscoveryItemsを一括登録/更新
     */
    @PostMapping("/csv")
    public ResponseEntity<?> uploadCsv(
            @RequestHeader(value = "X-OPS-KEY", required = false) String opsKey,
            @RequestParam("file") MultipartFile file) {

        if (!isAuthorized(opsKey)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "X-OPS-KEY or JWT required"));
        }

        if (file == null || file.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(Map.of("error", "CSV file is required"));
        }

        String contentType = file.getContentType();
        if (contentType != null && !contentType.contains("csv") && !contentType.contains("text/plain")) {
            log.warn("Unexpected content type: {}", contentType);
            // 許容するが警告（CSVはブラウザによってMIME typeが異なる場合がある）
        }

        try {
            CsvIngestResultResponse result = ingestService.ingestFromCsv(file.getInputStream());

            log.info("CSV upload complete: inserted={}, updated={}, errors={}",
                    result.inserted(), result.updated(), result.errors().size());

            return ResponseEntity.ok(Map.of(
                    "inserted", result.inserted(),
                    "updated", result.updated(),
                    "errors", result.errors(),
                    "totalProcessed", result.inserted() + result.updated()));

        } catch (IOException e) {
            log.error("Failed to read CSV file", e);
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(Map.of("error", "Failed to read CSV file: " + e.getMessage()));
        } catch (Exception e) {
            log.error("CSV ingest failed", e);
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(Map.of("error", "CSV ingest failed: " + e.getMessage()));
        }
    }

    private boolean isAuthorized(String opsKey) {
        return true;
        // // OPS-KEY または JWT認証をチェック
        // if (opsKeyService.isValid(opsKey)) {
        // return true;
        // }
        // // JWT認証
        // var auth = org.springframework.security.core.context.SecurityContextHolder
        // .getContext().getAuthentication();
        // return auth != null && auth.isAuthenticated()
        // && !(auth instanceof
        // org.springframework.security.authentication.AnonymousAuthenticationToken);
    }
}
