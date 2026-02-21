package com.example.cbs_mvp.gate;

import java.math.BigDecimal;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.cbs_mvp.service.GateResult;
import com.example.cbs_mvp.service.GateService;

@RestController
@RequestMapping("/gate")
public class GateController {

    private final GateService gateService;

    public GateController(GateService gateService) {
        this.gateService = gateService;
    }

    // newCostEstimateTotalYen is the estimated total cost for this decision.
    @PostMapping("/check")
    public ResponseEntity<?> check(@RequestBody GateCheckRequest req) {
        BigDecimal newCost = (req == null || req.newCostEstimateTotalYen() == null)
                ? BigDecimal.ZERO
                : req.newCostEstimateTotalYen();

        GateResult gr = gateService.checkCashGate(newCost);

        return ResponseEntity.ok(Map.of(
                "ok", gr.isOk(),
                "availableCash", gr.getAvailableCash(),
                "requiredCashBuffer", gr.getRequiredCashBuffer(),
                "refundReserve", gr.getRefundReserve(),
                "openCommitments", gr.getOpenCommitments()));
    }

    public record GateCheckRequest(BigDecimal newCostEstimateTotalYen) {
    }
}
