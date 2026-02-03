package com.example.cbs_mvp.ebay;

public interface EbayOrderClient {
    void uploadTracking(String ebayOrderKey, String carrier, String tracking);

    boolean checkTrackingUploaded(String ebayOrderKey);

    java.util.Map<String, Object> getOrder(String ebayOrderKey);
}
