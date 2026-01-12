package com.example.cbs_mvp.ebay;

import java.util.Map;

public interface EbayClient {
    void putInventoryItem(String sku, Map<String, Object> payload);
    String createOffer(String sku, Map<String, Object> payload);
    boolean checkOfferExists(String offerId);
}
