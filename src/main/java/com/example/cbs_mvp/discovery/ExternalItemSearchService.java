package com.example.cbs_mvp.discovery;

import com.example.cbs_mvp.dto.discovery.DiscoverySeed;
import java.util.List;

public interface ExternalItemSearchService {
    /**
     * Search for items using a keyword.
     * 
     * @param keyword Search keyword
     * @return List of DiscoverySeed items
     */
    List<DiscoverySeed> searchItems(String keyword);

    /**
     * Search for items within a price range across popular categories.
     * No keyword required — automatically browses recommended categories.
     *
     * @param minPrice Minimum price in JPY
     * @param maxPrice Maximum price in JPY
     * @return List of DiscoverySeed items
     */
    List<DiscoverySeed> searchByPriceRange(int minPrice, int maxPrice);

    /**
     * Get the source type name (e.g. "YAHOO", "RAKUTEN").
     */
    String getSourceType();
}
