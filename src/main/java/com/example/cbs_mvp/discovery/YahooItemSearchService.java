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
     * eBay輸出で人気のある検索キーワード
     */
    private static final List<String> POPULAR_KEYWORDS = List.of(
            "腕時計",
            "カメラ",
            "おもちゃ",
            "フィギュア",
            "ゲームソフト",
            "釣具",
            "スニーカー");

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

        for (String keyword : POPULAR_KEYWORDS) {
            try {
                String url = UriComponentsBuilder
                        .fromHttpUrl("https://shopping.yahooapis.jp/ShoppingWebService/V3/itemSearch")
                        .queryParam("appid", clientId)
                        .queryParam("query", keyword)
                        .queryParam("price_from", minPrice)
                        .queryParam("price_to", maxPrice)
                        .queryParam("results", 50)
                        .queryParam("sort", "-score")
                        .toUriString();

                List<DiscoverySeed> results = fetchItems(url);
                allResults.addAll(results);
                log.info("[Yahoo] keyword={} found={}", keyword, results.size());
                // Yahoo API: レートリミット対策として待機を追加
                Thread.sleep(1000);
            } catch (Exception e) {
                log.warn("[Yahoo] keyword={} error: {}", keyword, e.getMessage());
            }
        }

        return allResults;
    }

    @Override
    public String getSourceType() {
        return "YAHOO";
    }

    private boolean isConfigured() {
        return clientId != null && !clientId.isBlank()
                && !"CHANGE_ME".equals(clientId) && !clientId.startsWith("your-");
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
        String sellerId = hit.path("seller").path("sellerId").asText();
        String itemCode = hit.path("code").asText();

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
                "Imported from Yahoo. Seller: " + sellerId + " Code: " + itemCode);
    }
}
