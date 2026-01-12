package com.example.cbs_mvp.procurement;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.cbs_mvp.entity.CashLedger;
import com.example.cbs_mvp.entity.PurchaseOrder;
import com.example.cbs_mvp.repo.CashLedgerRepository;
import com.example.cbs_mvp.repo.PurchaseOrderRepository;
import com.example.cbs_mvp.service.StateTransitionService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProcurementService {

    private final PurchaseOrderRepository poRepo;
    private final CashLedgerRepository ledgerRepo;
    private final StateTransitionService transitions;

    /**
     * Freeze: PO作成とcash_ledger(PROCUREMENT)作成は不可分（同一Tx）
     */
    @Transactional
    public PurchaseOrder createPoAndLedger(CreatePoCommand cmd) {

        // 1) PO作成
        PurchaseOrder po = new PurchaseOrder();
        po.setOrderId(cmd.orderId());
        po.setSupplierName(cmd.supplierName());
        po.setSupplierOrderRef(cmd.supplierOrderRef());
        po.setShipTo3plAddress(cmd.shipTo3plAddress());
        po.setInboundTracking(cmd.inboundTracking());
        po.setExpectedTotalCostYen(cmd.expectedTotalCostYen());
        po.setState("REQUESTED");
        po.setCreatedAt(LocalDateTime.now());
        po.setUpdatedAt(LocalDateTime.now());

        po = poRepo.save(po);

        // 2) cash_ledger 作成（Commitment: actual_date NULL）
        CashLedger cl = new CashLedger();
        cl.setEventType("PROCUREMENT");
        cl.setRefTable("purchase_orders");
        cl.setRefId(po.getPoId());
        cl.setAmountYen(cmd.expectedTotalCostYen().negate()); // 出金なのでマイナス
        cl.setExpectedDate(null);
        cl.setActualDate(null); // ★Freeze: NULL=未確定=Commitment
        cl.setCreatedAt(LocalDateTime.now());

        ledgerRepo.save(cl);

        // 3) state_transitions（監査ログ）
        String cid = UUID.randomUUID().toString().replace("-", "");
        transitions.log("PO", po.getPoId(), null, "REQUESTED", "CREATE_PO", null, "SYSTEM", cid);

        return po;
    }

    @Transactional
    public CashLedger confirmPayment(Long poId) {
        CashLedger cl = ledgerRepo.findByRefTableAndRefIdAndEventType(
                "purchase_orders", poId, "PROCUREMENT"
        ).orElseThrow();

        cl.setActualDate(LocalDate.now());
        return ledgerRepo.save(cl);
    }

    public record CreatePoCommand(
        Long orderId,
        String supplierName,
        String supplierOrderRef,
        String shipTo3plAddress,
        String inboundTracking,
        BigDecimal expectedTotalCostYen
    ) {}
}
