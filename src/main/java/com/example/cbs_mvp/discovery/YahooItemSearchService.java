package com.example.cbs_mvp.discovery;

import com.example.cbs_mvp.dto.discovery.DiscoverySeed;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class YahooItemSearchService implements ExternalItemSearchService {

    private static final Logger log = LoggerFactory.getLogger(YahooItemSearchService.class);

    /**
     * eBay輸出で人気のある Yahoo Shopping カテゴリID
     * 時計・カメラ・ホビー・楽器・おもちゃ・ゲーム・アンティーク
     */
    private static final List<String> POPULAR_CATEGORIES = List.of(
            "2498", // 腕時計、アクセサリー
            "2510", // カメラ、光学機器
            "2277", // おもちゃ、ゲーム
            "2756", // 楽器、器材
            "26172", // コレクション
            "2084", // スポーツ
            "10002" // ファッション
    );

    @Value("${YAHOO_CLIENT_ID:}")
    private String clientId;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public List<DiscoverySeed> searchItems(String keyword) {
        if (!isConfigured())
            return List.of();

        String url = UriComponentsBuilder.fromHttpUrl("https://shopping.yahooapis.jp/ShoppingWebService/V3/itemSearch")
                .queryParam("appid", clientId)
                .queryParam("query", keyword)
                .queryParam("results", 20)
                .toUriString();

        return fetchItems(url);
    }

    @Override
    public List<DiscoverySeed> searchByPriceRange(int minPrice, int maxPrice) {
        if (!isConfigured())
            return List.of();

        List<DiscoverySeed> allResults = new ArrayList<>();

        for (String category : POPULAR_CATEGORIES) {
            try {
                String url = UriComponentsBuilder
                        .fromHttpUrl("https://shopping.yahooapis.jp/ShoppingWebService/V3/itemSearch")
                        .queryParam("appid", clientId)
                        .queryParam("category_id", category)
                        .queryParam("price_from", minPrice)
                        .queryParam("price_to", maxPrice)
                        .queryParam("results", 10)
                        .queryParam("sort", "-score")
                        .toUriString();

                List<DiscoverySeed> results = fetchItems(url);
                allResults.addAll(results);
                log.info("[Yahoo] category={} found={}", category, results.size());
            } catch (Exception e) {
                log.warn("[Yahoo] category={} error: {}", category, e.getMessage());
            }
        }

        return allResults;
    }

    @Override
    public String getSourceType() {
        return "YAHOO";
    }

    private boolean isConfigured() {
        return clientId != null && !clientId.isBlank() && !"CHANGE_ME".equals(clientId);
    }

    private List<DiscoverySeed> fetchItems(String url) {
        try {
            String response = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(response);
            JsonNode hits = root.path("hits");

            List<DiscoverySeed> results = new ArrayList<>();
            if (hits.isArray()) {
                for (JsonNode hit : hits) {
                    results.add(mapToSeed(hit));
                }
            }
            return results;
        } catch (Exception e) {
            log.error("[Yahoo] fetch error: {}", e.getMessage());
            return List.of();
        }
    }

    private DiscoverySeed mapToSeed(JsonNode hit) {
        String url = hit.path("url").asText();
        String name = hit.path("name").asText();
        BigDecimal price = new BigDecimal(hit.path("price").asInt());
        String condition = hit.path("condition").asText("new");

        String normalizedCondition = "new".equalsIgnoreCase(condition) ? "NEW" : "USED";

        return new DiscoverySeed(
                url,
                name,
                normalizedCondition,
                "RETAIL",
                "Yahoo Shopping",
                price,
                null,
                null,
                "Imported from Yahoo Shopping");
    }
}
