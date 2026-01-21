package com.example.cbs_mvp.ebay;

import com.example.cbs_mvp.ops.SystemFlagService;

public class StubEbayOrderClient implements EbayOrderClient {

    private static final String FAIL_PREFIX_KEY = "EBAY_STUB_TRACKING_FAIL_ORDER_PREFIX";
    private static final String MISSING_PREFIX_KEY = "EBAY_STUB_TRACKING_MISSING_ORDER_PREFIX";

    private final SystemFlagService flags;

    public StubEbayOrderClient(SystemFlagService flags) {
        this.flags = flags;
    }

    @Override
    public void uploadTracking(String ebayOrderKey, String carrier, String tracking) {
        String failPrefix = nz(flags.get(FAIL_PREFIX_KEY));
        if (!failPrefix.isBlank() && ebayOrderKey != null && ebayOrderKey.startsWith(failPrefix)) {
            throw new EbayOrderClientException(
                    "stub tracking upload failed for orderKey=" + ebayOrderKey,
                    true);
        }
    }

    @Override
    public boolean checkTrackingUploaded(String ebayOrderKey) {
        if (ebayOrderKey == null || ebayOrderKey.isBlank())
            return false;
        String missingPrefix = nz(flags.get(MISSING_PREFIX_KEY));
        if (!missingPrefix.isBlank() && ebayOrderKey.startsWith(missingPrefix)) {
            return false;
        }
        return true;
    }

    private static String nz(String v) {
        return v == null ? "" : v.trim();
    }
}
