package com.example.cbs_mvp.cash;

import java.math.BigDecimal;
import java.time.Instant;

import org.springframework.stereotype.Service;

import com.example.cbs_mvp.ops.SystemFlagService;
import com.example.cbs_mvp.repo.PurchaseOrderRepository;
import com.example.cbs_mvp.service.GateResult;
import com.example.cbs_mvp.service.GateService;

@Service
public class CashService {

    private final SystemFlagService flags;
    private final PurchaseOrderRepository poRepo;
    private final GateService gateService;

    public CashService(SystemFlagService flags, PurchaseOrderRepository poRepo, GateService gateService) {
        this.flags = flags;
        this.poRepo = poRepo;
        this.gateService = gateService;
    }

    public CashStatusResponse getStatus() {
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

        BigDecimal capLimit = recentSales30d.multiply(wcCapRatio);
        BigDecimal capUsed = openCommitments;

        return new CashStatusResponse(
                currentCash,
                creditLimit,
                creditUsed,
                openCommitments,
                refundReserve,
                wcAvailable,
                capLimit,
                capUsed,
                Instant.now().toString()
        );
    }

    public GateResult checkGate(BigDecimal newCostEstimateTotalYen) {
        return gateService.checkCashGate(newCostEstimateTotalYen);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static BigDecimal bd(String s, String def) {
        String v = (s == null || s.isBlank()) ? def : s.trim();
        return new BigDecimal(v);
    }
}
