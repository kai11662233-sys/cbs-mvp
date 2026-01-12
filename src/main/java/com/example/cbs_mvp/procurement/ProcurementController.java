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

    // Deprecated: use /procurement/request
    @PostMapping("/po")
    public ResponseEntity<?> createPo(@RequestBody ProcurementRequest req) {
        return requestProcurement(req);
    }

    @PostMapping("/request")
    public ResponseEntity<?> requestProcurement(@RequestBody ProcurementRequest req) {
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

    @PostMapping("/{poId}/confirm-payment")
    public ResponseEntity<?> confirmPayment(@PathVariable Long poId) {
        try {
            var cl = procurementService.confirmPayment(poId);
            return ResponseEntity.ok(Map.of(
                    "poId", poId,
                    "cashId", cl.getCashId(),
                    "actualDate", cl.getActualDate()
            ));
        } catch (java.util.NoSuchElementException ex) {
            return ResponseEntity.status(404).body(Map.of("error", "cash_ledger not found"));
        }
    }

    public static class ProcurementRequest {
        public Long orderId;
        public BigDecimal expectedTotalCostYen;
        public String shipTo3plAddress;
        public String supplierName;
        public String supplierOrderRef;
        public String inboundTracking;
    }
}
