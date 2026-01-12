package com.example.cbs_mvp.cash;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;

import com.example.cbs_mvp.ops.OpsKeyService;
import com.example.cbs_mvp.ops.SystemFlagService;
import com.example.cbs_mvp.service.GateResult;
import com.example.cbs_mvp.service.StateTransitionService;

@RestController
@RequestMapping("/cash")
public class CashController {

    private static final Set<String> ALLOWED_KEYS = Set.of(
            "CURRENT_CASH",
            "CREDIT_LIMIT",
            "CREDIT_USED",
            "UNCONFIRMED_COST",
            "REFUND_FIX_RES",
            "RECENT_SALES_30D",
            "REFUND_RES_RATIO",
            "WC_CAP_RATIO"
    );

    private final CashService cashService;
    private final SystemFlagService flags;
    private final OpsKeyService opsKeyService;
    private final StateTransitionService transitions;

    public CashController(
            CashService cashService,
            SystemFlagService flags,
            OpsKeyService opsKeyService,
            StateTransitionService transitions
    ) {
        this.cashService = cashService;
        this.flags = flags;
        this.opsKeyService = opsKeyService;
        this.transitions = transitions;
    }

    @GetMapping("/status")
    public ResponseEntity<?> status() {
        return ResponseEntity.ok(cashService.getStatus());
    }

    @PostMapping("/gate-check")
    public ResponseEntity<?> gateCheck(@RequestBody GateCheckRequest req) {
        if (req == null || req.newCostEstimateTotalYen == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "newCostEstimateTotalYen is required"));
        }
        if (req.newCostEstimateTotalYen.signum() < 0) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "newCostEstimateTotalYen must be >= 0"));
        }

        GateResult gr = cashService.checkGate(req.newCostEstimateTotalYen);
        return ResponseEntity.ok(Map.of(
                "ok", gr.isOk(),
                "capOk", gr.isCapOk(),
                "wcAvailableYen", gr.getWcAvailable(),
                "refundReserveYen", gr.getRefundReserve(),
                "openCommitmentsYen", gr.getOpenCommitments(),
                "newCostEstimateTotalYen", req.newCostEstimateTotalYen,
                "ts", Instant.now().toString()
        ));
    }

    @PostMapping("/flags")
    @Transactional
    public ResponseEntity<?> updateFlags(
            @RequestHeader(value = "X-OPS-KEY", required = false) String opsKey,
            @RequestBody(required = false) Map<String, Object> body
    ) {
        if (!opsKeyService.isValid(opsKey)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "invalid ops key"));
        }
        if (body == null || body.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "request body is required"));
        }

        for (String key : body.keySet()) {
            if (!ALLOWED_KEYS.contains(key)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "unknown key: " + key));
            }
        }

        Set<String> updatedKeys = new TreeSet<>();
        for (Map.Entry<String, Object> entry : body.entrySet()) {
            String key = entry.getKey();
            Object raw = entry.getValue();
            if (raw == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "value is required for key: " + key));
            }
            String value = String.valueOf(raw).trim();
            if (value.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "value is required for key: " + key));
            }
            BigDecimal num;
            try {
                num = new BigDecimal(value);
            } catch (NumberFormatException ex) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "invalid number for key: " + key));
            }
            if (isRatioKey(key)) {
                if (num.compareTo(BigDecimal.ZERO) < 0 || num.compareTo(BigDecimal.ONE) > 0) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(Map.of("error", "ratio must be between 0 and 1: " + key));
                }
            } else if (num.compareTo(BigDecimal.ZERO) < 0) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "value must be >= 0: " + key));
            }
            flags.set(key, value);
            updatedKeys.add(key);
        }

        transitions.log(
                "SYSTEM",
                0L,
                null,
                "CASH_FLAGS_UPDATED",
                "CASH_FLAGS_UPDATED",
                "updated keys: " + String.join(",", updatedKeys),
                "SYSTEM",
                cid()
        );

        return ResponseEntity.ok(cashService.getStatus());
    }

    private static String cid() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static boolean isRatioKey(String key) {
        return "REFUND_RES_RATIO".equals(key) || "WC_CAP_RATIO".equals(key);
    }

    public static class GateCheckRequest {
        public BigDecimal newCostEstimateTotalYen;
    }
}
