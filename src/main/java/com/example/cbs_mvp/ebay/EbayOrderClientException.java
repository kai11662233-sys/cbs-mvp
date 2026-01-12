package com.example.cbs_mvp.ebay;

public class EbayOrderClientException extends RuntimeException {
    private final boolean retryable;

    public EbayOrderClientException(String message, boolean retryable) {
        super(message);
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
