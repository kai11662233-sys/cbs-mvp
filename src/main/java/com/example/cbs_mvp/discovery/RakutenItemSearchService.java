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

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public List<DiscoverySeed> searchItems(String keyword) {
        if (!isConfigured())
            return List.of();

        String url = UriComponentsBuilder
                .fromHttpUrl("https://app.rakuten.co.jp/services/api/IchibaItem/Search/20220601")
                .queryParam("applicationId", applicationId)
                .queryParam("keyword", keyword)
                .queryParam("hits", 20)
                .toUriString();

        return fetchItems(url);
    }

    @Override
    public List<DiscoverySeed> searchByPriceRange(int minPrice, int maxPrice) {
        if (!isConfigured())
            return List.of();

        List<DiscoverySeed> allResults = new ArrayList<>();

        for (Integer genreId : POPULAR_GENRE_IDS) {
            try {
                String url = UriComponentsBuilder
                        .fromHttpUrl("https://app.rakuten.co.jp/services/api/IchibaItem/Search/20220601")
                        .queryParam("applicationId", applicationId)
                        .queryParam("genreId", genreId)
                        .queryParam("minPrice", minPrice)
                        .queryParam("maxPrice", maxPrice)
                        .queryParam("hits", 10)
                        .queryParam("sort", "-reviewCount")
                        .toUriString();

                List<DiscoverySeed> results = fetchItems(url);
                allResults.addAll(results);
                log.info("[Rakuten] genreId={} found={}", genreId, results.size());
            } catch (Exception e) {
                log.warn("[Rakuten] genreId={} error: {}", genreId, e.getMessage());
            }
        }

        return allResults;
    }

    @Override
    public String getSourceType() {
        return "RAKUTEN";
    }

    private boolean isConfigured() {
        return applicationId != null && !applicationId.isBlank() && !"CHANGE_ME".equals(applicationId);
    }

    private List<DiscoverySeed> fetchItems(String url) {
        try {
            String response = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(response);
            JsonNode items = root.path("Items");

            List<DiscoverySeed> results = new ArrayList<>();
            if (items.isArray()) {
                for (JsonNode itemWrapper : items) {
                    JsonNode item = itemWrapper.path("Item");
                    results.add(mapToSeed(item));
                }
            }
            return results;
        } catch (Exception e) {
            log.error("[Rakuten] fetch error: {}", e.getMessage());
            return List.of();
        }
    }

    private DiscoverySeed mapToSeed(JsonNode item) {
        String url = item.path("itemUrl").asText();
        String name = item.path("itemName").asText();
        BigDecimal price = new BigDecimal(item.path("itemPrice").asInt());
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
                "Imported from Rakuten Ichiba");
    }
}
