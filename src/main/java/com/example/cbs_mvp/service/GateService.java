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

    public GateResult checkCashGate(BigDecimal newCostEstimateTotalYen) {
        BigDecimal currentCash = bd(flags.get("CURRENT_CASH"), "0");
        BigDecimal creditLimit = bd(flags.get("CREDIT_LIMIT"), "0");
        BigDecimal creditUsed = bd(flags.get("CREDIT_USED"), "0");
        BigDecimal unconfirmedCost = bd(flags.get("UNCONFIRMED_COST"), "0");
        BigDecimal refundFixRes = bd(flags.get("REFUND_FIX_RES"), "0");
        BigDecimal recentSales30d = bd(flags.get("RECENT_SALES_30D"), "0");
        BigDecimal refundResRatio = bd(flags.get("REFUND_RES_RATIO"), "0.10");
        BigDecimal wcCapRatio = bd(flags.get("WC_CAP_RATIO"), "0.30");

        BigDecimal openCommitments = nz(poRepo.calculateOpenCommitments());
        BigDecimal refundReserve = refundFixRes.max(recentSales30d.multiply(refundResRatio));

        BigDecimal creditAvailable = creditLimit.subtract(creditUsed);
        if (creditAvailable.signum() < 0) {
            creditAvailable = BigDecimal.ZERO;
        }

        BigDecimal wcAvailable = currentCash
                .add(creditAvailable)
                .subtract(unconfirmedCost)
                .subtract(refundReserve)
                .subtract(openCommitments);

        BigDecimal cap = recentSales30d.multiply(wcCapRatio);
        BigDecimal totalCommitments = openCommitments.add(nz(newCostEstimateTotalYen));

        // 純粋な現金余裕（信用枠を含まない）
        BigDecimal pureCashAvailable = currentCash
                .subtract(unconfirmedCost)
                .subtract(refundReserve)
                .subtract(openCommitments);

        boolean capOk = totalCommitments.compareTo(cap) <= 0;
        boolean coveredByCash = pureCashAvailable.compareTo(nz(newCostEstimateTotalYen)) >= 0;

        // Cap制限は、現金が足りず信用枠に頼る場合の急拡大防止とする。
        // 現金でフルカバーできるなら、Capを超えていても許可する。
        boolean ok = (capOk || coveredByCash) && wcAvailable.compareTo(nz(newCostEstimateTotalYen)) >= 0;

        return new GateResult(ok, capOk, wcAvailable, refundReserve, openCommitments);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static BigDecimal bd(String s, String def) {
        String v = (s == null || s.isBlank()) ? def : s.trim();
        return new BigDecimal(v);
    }
}
