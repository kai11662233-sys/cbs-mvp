package com.example.cbs_mvp.dto.discovery;

/**
 * CSV取り込みエラー情報
 */
public record CsvIngestError(
        int row,
        String message,
        String raw) {
}
