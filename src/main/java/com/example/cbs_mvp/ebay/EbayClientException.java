package com.example.cbs_mvp.ebay;

public class EbayClientException extends RuntimeException {
    private final boolean offerError;

    public EbayClientException(String message, boolean offerError) {
        super(message);
        this.offerError = offerError;
    }

    public boolean isOfferError() {
        return offerError;
    }
}
