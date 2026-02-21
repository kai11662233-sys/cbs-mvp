package com.example.cbs_mvp.service;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;

import com.example.cbs_mvp.ops.SystemFlagService;
import com.example.cbs_mvp.repo.PurchaseOrderRepository;

@Service
public class GateService {

    private final SystemFlagService flags;
    private final PurchaseOrderRepository poRepo;

    public GateService(SystemFlagService flags, PurchaseOrderRepository poRepo) {
        this.flags = flags;
        this.poRepo = poRepo;
    }

    /**
     * Cash Gate: AvailableCash = CashOnHand - OpenCommitments - ReserveHeld
     * Gate OK when: AvailableCash >= RequiredCashBuffer + newCostEstimate
     */
    public GateResult checkCashGate(BigDecimal newCostEstimateTotalYen) {
        BigDecimal cashOnHand = bd(flags.get("CURRENT_CASH"), "0");
        BigDecimal requiredCashBuffer = bd(flags.get("REQUIRED_CASH_BUFFER"), "50000");

        // Reserve: max(fixed reserve, sales * ratio)
        BigDecimal refundFixRes = bd(flags.get("REFUND_FIX_RES"), "0");
        BigDecimal recentSales30d = bd(flags.get("RECENT_SALES_30D"), "0");
        BigDecimal refundResRatio = bd(flags.get("REFUND_RES_RATIO"), "0.10");
        BigDecimal refundReserve = refundFixRes.max(recentSales30d.multiply(refundResRatio));

        BigDecimal openCommitments = nz(poRepo.calculateOpenCommitments());

        // AvailableCash = CashOnHand - OpenCommitments - ReserveHeld
        BigDecimal availableCash = cashOnHand
                .subtract(openCommitments)
                .subtract(refundReserve);

        // Gate: AvailableCash >= RequiredCashBuffer + newCost
        BigDecimal threshold = requiredCashBuffer.add(nz(newCostEstimateTotalYen));
        boolean ok = availableCash.compareTo(threshold) >= 0;

        return new GateResult(ok, availableCash, refundReserve, openCommitments, requiredCashBuffer);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static BigDecimal bd(String s, String def) {
        String v = (s == null || s.isBlank()) ? def : s.trim();
        return new BigDecimal(v);
    }
}
