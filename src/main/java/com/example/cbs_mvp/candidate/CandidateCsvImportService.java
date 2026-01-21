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

            // BOM除去
            if (headerLine.startsWith("\uFEFF")) {
                headerLine = headerLine.substring(1);
            }

            // ヘッダー解析
            String[] headers = parseCsvLine(headerLine);
            int urlIdx = findIndex(headers, "sourceUrl", "url", "source_url");
            int priceIdx = findIndex(headers, "sourcePriceYen", "price", "source_price_yen");
            int weightIdx = findIndex(headers, "weightKg", "weight", "weight_kg");
            int sizeIdx = findIndex(headers, "sizeTier", "size", "size_tier");

            if (urlIdx < 0) {
                return new ImportResult(0, 0, List.of("Missing required column: sourceUrl"));
            }
            if (priceIdx < 0) {
                return new ImportResult(0, 0, List.of("Missing required column: sourcePriceYen"));
            }

            String line;
            while ((line = reader.readLine()) != null) {
                lineNumber++;

                if (line.isBlank()) {
                    continue;
                }

                try {
                    String[] values = parseCsvLine(line);

                    // sourceUrl 検証
                    String url = getValueSafe(values, urlIdx, "");
                    if (url.isBlank()) {
                        errors.add("Line " + lineNumber + ": sourceUrl is required");
                        continue;
                    }

                    // sourcePriceYen 検証（必須）
                    String priceStr = getValueSafe(values, priceIdx, "");
                    if (priceStr.isBlank()) {
                        errors.add("Line " + lineNumber + ": sourcePriceYen is required");
                        continue;
                    }
                    BigDecimal price = parseBigDecimalStrict(priceStr);
                    if (price == null) {
                        errors.add("Line " + lineNumber + ": invalid sourcePriceYen: " + priceStr);
                        continue;
                    }
                    if (price.compareTo(BigDecimal.ZERO) <= 0) {
                        errors.add("Line " + lineNumber + ": sourcePriceYen must be positive");
                        continue;
                    }

                    // 重複チェック
                    if (candidateRepo.findBySourceUrl(url).isPresent()) {
                        errors.add("Line " + lineNumber + ": duplicate URL: " + truncate(url, 50));
                        continue;
                    }

                    // weightKg 検証（オプション、あれば正の数）
                    BigDecimal weight = null;
                    String weightStr = getValueSafe(values, weightIdx, null);
                    if (weightStr != null && !weightStr.isBlank()) {
                        weight = parseBigDecimalStrict(weightStr);
                        if (weight == null || weight.compareTo(BigDecimal.ZERO) <= 0) {
                            errors.add("Line " + lineNumber + ": invalid weightKg: " + weightStr);
                            continue;
                        }
                    }

                    // sizeTier 検証（オプション）
                    String sizeTier = getValueSafe(values, sizeIdx, null);
                    if (sizeTier != null && !sizeTier.isBlank()) {
                        sizeTier = sizeTier.trim().toUpperCase();
                        if (!isValidSizeTier(sizeTier)) {
                            errors.add("Line " + lineNumber + ": invalid sizeTier: " + sizeTier
                                    + " (expected: S/M/L/XL/XXL)");
                            continue;
                        }
                    }

                    Candidate candidate = new Candidate();
                    candidate.setSourceUrl(url);
                    candidate.setSourcePriceYen(price);
                    candidate.setWeightKg(weight);
                    candidate.setSizeTier(sizeTier);
                    candidate.setState("CANDIDATE");

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

    /**
     * 厳密なBigDecimalパース。失敗時はnullを返す。
     */
    private BigDecimal parseBigDecimalStrict(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            // カンマ、円記号、スペースを除去
            String cleaned = value.replace(",", "")
                    .replace("¥", "")
                    .replace("￥", "")
                    .replace(" ", "")
                    .trim();
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean isValidSizeTier(String tier) {
        return tier != null && ("S".equals(tier) || "M".equals(tier) || "L".equals(tier) ||
                "XL".equals(tier) || "XXL".equals(tier));
    }

    private String truncate(String s, int maxLen) {
        if (s == null)
            return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }

    /**
     * CSVパース（引用符、エスケープ対応）
     * ダブルクォート内の "" はエスケープとして扱う
     */
    private String[] parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        char[] chars = line.toCharArray();

        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];

            if (inQuotes) {
                if (c == '"') {
                    // 次も " ならエスケープ
                    if (i + 1 < chars.length && chars[i + 1] == '"') {
                        current.append('"');
                        i++; // skip next quote
                    } else {
                        inQuotes = false;
                    }
                } else {
                    current.append(c);
                }
            } else {
                if (c == '"') {
                    inQuotes = true;
                } else if (c == ',') {
                    values.add(current.toString());
                    current = new StringBuilder();
                } else {
                    current.append(c);
                }
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
