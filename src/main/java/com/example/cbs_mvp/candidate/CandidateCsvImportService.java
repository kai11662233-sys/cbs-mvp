package com.example.cbs_mvp.candidate;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.cbs_mvp.entity.Candidate;
import com.example.cbs_mvp.repo.CandidateRepository;
import com.example.cbs_mvp.service.StateTransitionService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CandidateCsvImportService {

    private static final Logger log = LoggerFactory.getLogger(CandidateCsvImportService.class);

    private final CandidateRepository candidateRepo;
    private final StateTransitionService transitions;

    /**
     * CSVからCandidateを一括インポート
     * 
     * CSV形式 (ヘッダー必須):
     * sourceUrl,sourcePriceYen,weightKg,sizeTier,title
     * 
     * @return インポート結果
     */
    @Transactional
    public ImportResult importFromCsv(InputStream inputStream) {
        List<String> errors = new ArrayList<>();
        int successCount = 0;
        int lineNumber = 0;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {

            String headerLine = reader.readLine();
            lineNumber++;

            if (headerLine == null) {
                return new ImportResult(0, 0, List.of("Empty file"));
            }

            // ヘッダー解析
            String[] headers = headerLine.split(",");
            int urlIdx = findIndex(headers, "sourceUrl", "url", "source_url");
            int priceIdx = findIndex(headers, "sourcePriceYen", "price", "source_price_yen");
            int weightIdx = findIndex(headers, "weightKg", "weight", "weight_kg");
            int sizeIdx = findIndex(headers, "sizeTier", "size", "size_tier");
            int titleIdx = findIndex(headers, "title", "name");

            if (urlIdx < 0) {
                return new ImportResult(0, 0, List.of("Missing required column: sourceUrl"));
            }

            String line;
            while ((line = reader.readLine()) != null) {
                lineNumber++;

                if (line.isBlank()) {
                    continue;
                }

                try {
                    String[] values = parseCsvLine(line);

                    String url = getValueSafe(values, urlIdx, "");
                    if (url.isBlank()) {
                        errors.add("Line " + lineNumber + ": sourceUrl is required");
                        continue;
                    }

                    // 重複チェック
                    if (candidateRepo.findBySourceUrl(url).isPresent()) {
                        errors.add("Line " + lineNumber + ": duplicate URL: " + url);
                        continue;
                    }

                    Candidate candidate = new Candidate();
                    candidate.setSourceUrl(url);
                    candidate.setSourcePriceYen(parseBigDecimal(getValueSafe(values, priceIdx, "0")));
                    candidate.setWeightKg(parseBigDecimal(getValueSafe(values, weightIdx, null)));
                    candidate.setSizeTier(getValueSafe(values, sizeIdx, null));
                    candidate.setState("CANDIDATE");

                    if (titleIdx >= 0 && titleIdx < values.length) {
                        // タイトルは将来の拡張用
                    }

                    candidateRepo.save(candidate);

                    transitions.log(
                            "CANDIDATE",
                            candidate.getCandidateId(),
                            null,
                            "CANDIDATE",
                            "CSV_IMPORT",
                            "imported from CSV line " + lineNumber,
                            "SYSTEM",
                            java.util.UUID.randomUUID().toString().replace("-", ""));

                    successCount++;

                } catch (Exception e) {
                    errors.add("Line " + lineNumber + ": " + e.getMessage());
                }
            }

        } catch (IOException e) {
            log.error("CSV import failed", e);
            errors.add("IO error: " + e.getMessage());
        }

        log.info("CSV import completed: {} success, {} errors", successCount, errors.size());
        return new ImportResult(successCount, errors.size(), errors);
    }

    private int findIndex(String[] headers, String... names) {
        for (int i = 0; i < headers.length; i++) {
            String h = headers[i].trim().toLowerCase();
            for (String name : names) {
                if (h.equals(name.toLowerCase())) {
                    return i;
                }
            }
        }
        return -1;
    }

    private String getValueSafe(String[] values, int index, String defaultValue) {
        if (index < 0 || index >= values.length) {
            return defaultValue;
        }
        String val = values[index].trim();
        return val.isEmpty() ? defaultValue : val;
    }

    private BigDecimal parseBigDecimal(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value.replace(",", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String[] parseCsvLine(String line) {
        // 簡易CSVパース（引用符対応）
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (char c : line.toCharArray()) {
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                values.add(current.toString());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        values.add(current.toString());

        return values.toArray(new String[0]);
    }

    public record ImportResult(int successCount, int errorCount, List<String> errors) {
        public boolean hasErrors() {
            return errorCount > 0;
        }
    }
}
