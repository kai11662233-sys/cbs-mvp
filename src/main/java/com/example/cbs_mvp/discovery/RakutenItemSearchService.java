package com.example.cbs_mvp.discovery;

import com.example.cbs_mvp.dto.discovery.DiscoverySeed;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class RakutenItemSearchService implements ExternalItemSearchService {

    private static final Logger log = LoggerFactory.getLogger(RakutenItemSearchService.class);

    /**
     * eBay輸出で人気のある楽天ジャンルID
     * 時計 → 558885, カメラ → 562637, ホビー → 101164,
     * フィギュア → 212514, 楽器 → 510915, スポーツ → 101070
     */
    private static final List<Integer> POPULAR_GENRE_IDS = List.of(
            558885, // 腕時計
            562637, // カメラ・ビデオカメラ
            101164, // ホビー
            212514, // フィギュア
            510915, // 楽器・音響機器
            101070, // スポーツ・アウトドア
            100227 // ファッション・バッグ
    );

    @Value("${RAKUTEN_APPLICATION_ID:}")
    private String applicationId;

    @Value("${RAKUTEN_ACCESS_KEY:}")
    private String accessKey;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RakutenItemSearchService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public List<DiscoverySeed> searchItems(String keyword) {
        if (!isConfigured())
            return List.of();

        String url = UriComponentsBuilder
                .fromHttpUrl("https://openapi.rakuten.co.jp/ichibams/api/IchibaItem/Search/20220601")
                .queryParam("applicationId", applicationId)
                .queryParam("accessKey", accessKey)
                .queryParam("keyword", keyword)
                .queryParam("hits", 20)
                .queryParam("format", "json")
                .queryParam("formatVersion", 2)
                .toUriString();

        return fetchItems(url);
    }

    @Override
    public List<DiscoverySeed> searchByPriceRange(int minPrice, int maxPrice) {
        if (!isConfigured())
            return List.of();

        List<DiscoverySeed> allResults = new ArrayList<>();

        for (Integer genreId : POPULAR_GENRE_IDS) {
            for (int page = 1; page <= 3; page++) {
                try {
                    String url = UriComponentsBuilder
                            .fromHttpUrl("https://openapi.rakuten.co.jp/ichibams/api/IchibaItem/Search/20220601")
                            .queryParam("applicationId", applicationId)
                            .queryParam("accessKey", accessKey)
                            .queryParam("genreId", genreId)
                            .queryParam("minPrice", minPrice)
                            .queryParam("maxPrice", maxPrice)
                            .queryParam("page", page)
                            .queryParam("hits", 30)
                            .queryParam("sort", "-updateTimestamp")
                            .queryParam("format", "json")
                            .queryParam("formatVersion", 2)
                            .toUriString();

                    List<DiscoverySeed> results = fetchItems(url);
                    allResults.addAll(results);
                    log.info("[Rakuten] genreId={} page={} found={}", genreId, page, results.size());
                    // 楽天API: 1秒1リクエスト制限を回避
                    Thread.sleep(1200);
                } catch (Exception e) {
                    log.warn("[Rakuten] genreId={} page={} error: {}", genreId, page, e.getMessage());
                }
            }
        }

        return allResults;
    }

    @Override
    public String getSourceType() {
        return "RAKUTEN";
    }

    private boolean isConfigured() {
        return applicationId != null && !applicationId.isBlank()
                && !"CHANGE_ME".equals(applicationId) && !applicationId.startsWith("your-")
                && accessKey != null && !accessKey.isBlank()
                && !"CHANGE_ME".equals(accessKey) && !accessKey.startsWith("your-");
    }

    private List<DiscoverySeed> fetchItems(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Referer", "https://example.com/cbs-mvp-poc")
                    .header("Origin", "https://example.com")
                    .header("User-Agent", "Mozilla/5.0")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("[Rakuten] fetch HTTP error: {} - {}", response.statusCode(), response.body());
                return List.of();
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode items = root.path("Items");

            List<DiscoverySeed> results = new ArrayList<>();
            if (items.isArray()) {
                for (JsonNode item : items) {
                    results.add(mapToSeed(item));
                }
            }
            return results;
        } catch (Exception e) {
            log.error("[Rakuten] fetch error: {}", e.getMessage(), e);
            return List.of();
        }
    }

    private DiscoverySeed mapToSeed(JsonNode item) {
        String url = item.path("itemUrl").asText();
        String name = item.path("itemName").asText();
        BigDecimal price = new BigDecimal(item.path("itemPrice").asInt());
        String itemCode = item.path("itemCode").asText();
        String condition = "NEW";

        return new DiscoverySeed(
                url,
                name,
                condition,
                "RETAIL",
                "Rakuten Ichiba",
                price,
                null,
                null,
                "Imported from Rakuten Ichiba. Code: " + itemCode);
    }
}
