package com.example.cbs_mvp.ebay;

import java.util.Map;

import com.example.cbs_mvp.ops.SystemFlagService;

public class StubEbayClient implements EbayClient {

    private static final String FAIL_PREFIX_KEY = "EBAY_STUB_FAIL_SKU_PREFIX";
    private static final String OFFER_FAIL_PREFIX_KEY = "EBAY_STUB_OFFER_FAIL_SKU_PREFIX";
    private static final String OFFER_MISSING_PREFIX_KEY = "EBAY_STUB_OFFER_MISSING_SKU_PREFIX";

    private final SystemFlagService flags;

    public StubEbayClient(SystemFlagService flags) {
        this.flags = flags;
    }

    @Override
    public void putInventoryItem(String sku, Map<String, Object> payload) {
        String failPrefix = flags.get(FAIL_PREFIX_KEY);
        if (failPrefix != null && !failPrefix.isBlank() && sku.startsWith(failPrefix)) {
            throw new EbayClientException("stub inventory failure for sku=" + sku, false);
        }
    }

    @Override
    public String createOffer(String sku, Map<String, Object> payload) {
        String offerFailPrefix = flags.get(OFFER_FAIL_PREFIX_KEY);
        if (offerFailPrefix != null && !offerFailPrefix.isBlank() && sku.startsWith(offerFailPrefix)) {
            throw new EbayClientException("stub offer failure for sku=" + sku, true);
        }
        return "OFFER-" + sku;
    }

    @Override
    public boolean checkOfferExists(String offerId) {
        if (offerId == null || offerId.isBlank()) {
            return false;
        }
        String missingPrefix = flags.get(OFFER_MISSING_PREFIX_KEY);
        if (missingPrefix != null && !missingPrefix.isBlank() && offerId.startsWith("OFFER-")) {
            String sku = offerId.substring("OFFER-".length());
            return !sku.startsWith(missingPrefix);
        }
        return true;
    }
}
