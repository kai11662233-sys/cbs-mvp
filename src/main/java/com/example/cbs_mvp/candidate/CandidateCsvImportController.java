package com.example.cbs_mvp.candidate;

import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/candidates")
@RequiredArgsConstructor
public class CandidateCsvImportController {

    private final CandidateCsvImportService csvImportService;

    /**
     * CSVファイルからCandidateを一括インポート
     * 
     * POST /candidates/import-csv
     * Content-Type: multipart/form-data
     * Body: file=@candidates.csv
     */
    @PostMapping(value = "/import-csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> importCsv(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "File is required"));
        }

        try {
            CandidateCsvImportService.ImportResult result = csvImportService.importFromCsv(file.getInputStream());

            return ResponseEntity.ok(Map.of(
                    "successCount", result.successCount(),
                    "errorCount", result.errorCount(),
                    "errors", result.errors().stream().limit(20).toList(),
                    "message", result.hasErrors()
                            ? "Import completed with errors"
                            : "Import completed successfully"));

        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Import failed: " + e.getMessage()));
        }
    }

    /**
     * CSVファイルのプレビュー/バリデーション（取り込みはしない）
     * 
     * POST /candidates/import-csv/preview
     * Content-Type: multipart/form-data
     * Body: file=@candidates.csv
     * 
     * UIで取り込み前にエラーを確認するために使用
     */
    @PostMapping(value = "/import-csv/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> previewCsv(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "File is required"));
        }

        try {
            CandidateCsvImportService.PreviewResult result = csvImportService.previewFromCsv(file.getInputStream());

            return ResponseEntity.ok(Map.of(
                    "totalRows", result.totalRows(),
                    "validRows", result.validRows(),
                    "errorCount", result.errors().size(),
                    "errors", result.errors().stream().limit(50).toList(),
                    "preview", result.previewData().stream().limit(10).toList(),
                    "canImport", result.errors().isEmpty(),
                    "message", result.errors().isEmpty()
                            ? "Validation passed - ready to import"
                            : "Validation errors found - please fix before importing"));

        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Preview failed: " + e.getMessage()));
        }
    }
}
