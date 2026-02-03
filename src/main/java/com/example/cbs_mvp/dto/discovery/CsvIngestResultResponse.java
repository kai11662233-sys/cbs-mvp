package com.example.cbs_mvp.dto.discovery;

import java.util.List;

/**
 * CSV取り込み結果レスポンス
 */
public record CsvIngestResultResponse(
        int inserted,
        int updated,
        List<CsvIngestError> errors) {
}
