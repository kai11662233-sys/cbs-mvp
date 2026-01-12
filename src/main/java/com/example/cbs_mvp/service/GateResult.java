package com.example.cbs_mvp.service;

import java.math.BigDecimal;

public class GateResult {

    private final boolean ok;
    private final boolean capOk;
    private final BigDecimal wcAvailable;
    private final BigDecimal refundReserve;
    private final BigDecimal openCommitments;

    public GateResult(
            boolean ok,
            boolean capOk,
            BigDecimal wcAvailable,
            BigDecimal refundReserve,
            BigDecimal openCommitments
    ) {
        this.ok = ok;
        this.capOk = capOk;
        this.wcAvailable = wcAvailable;
        this.refundReserve = refundReserve;
        this.openCommitments = openCommitments;
    }

    public boolean isOk() {
        return ok;
    }

    public boolean isCapOk() {
        return capOk;
    }

    public BigDecimal getWcAvailable() {
        return wcAvailable;
    }

    public BigDecimal getRefundReserve() {
        return refundReserve;
    }

    public BigDecimal getOpenCommitments() {
        return openCommitments;
    }
}
