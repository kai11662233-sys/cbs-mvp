package com.example.cbs_mvp.service;

import java.math.BigDecimal;

public class GateResult {

    private final boolean ok;
    private final BigDecimal availableCash;
    private final BigDecimal refundReserve;
    private final BigDecimal openCommitments;
    private final BigDecimal requiredCashBuffer;

    public GateResult(
            boolean ok,
            BigDecimal availableCash,
            BigDecimal refundReserve,
            BigDecimal openCommitments,
            BigDecimal requiredCashBuffer) {
        this.ok = ok;
        this.availableCash = availableCash;
        this.refundReserve = refundReserve;
        this.openCommitments = openCommitments;
        this.requiredCashBuffer = requiredCashBuffer;
    }

    public boolean isOk() {
        return ok;
    }

    public BigDecimal getAvailableCash() {
        return availableCash;
    }

    public BigDecimal getRefundReserve() {
        return refundReserve;
    }

    public BigDecimal getOpenCommitments() {
        return openCommitments;
    }

    public BigDecimal getRequiredCashBuffer() {
        return requiredCashBuffer;
    }
}
