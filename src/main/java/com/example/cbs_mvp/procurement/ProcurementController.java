package com.example.cbs_mvp.procurement;

import java.math.BigDecimal;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.example.cbs_mvp.repo.PurchaseOrderRepository;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/procurement")
public class ProcurementController {

    private final ProcurementService procurementService;
    private final PurchaseOrderRepository poRepo;

    @PostMapping("/po")
    public ResponseEntity<?> createPo(@RequestBody CreatePoRequest req) {
        var po = procurementService.createPoAndLedger(new ProcurementService.CreatePoCommand(
            req.orderId,
            req.supplierName,
            req.supplierOrderRef,
            req.shipTo3plAddress,
            req.inboundTracking,
            req.expectedTotalCostYen
        ));
        return ResponseEntity.ok(Map.of("poId", po.getPoId(), "state", po.getState()));
    }

    @GetMapping("/open-commitments")
    public ResponseEntity<?> openCommitments() {
        BigDecimal v = poRepo.calculateOpenCommitments();
        return ResponseEntity.ok(Map.of("openCommitmentsYen", v));
    }

    public static class CreatePoRequest {
        public Long orderId;
        public String supplierName;
        public String supplierOrderRef;
        public String shipTo3plAddress;
        public String inboundTracking;
        public BigDecimal expectedTotalCostYen;
    }
}
