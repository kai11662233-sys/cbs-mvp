package com.example.cbs_mvp.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.cbs_mvp.entity.CashLedger;
import com.example.cbs_mvp.entity.Order;
import com.example.cbs_mvp.ops.KillSwitchService;
import com.example.cbs_mvp.repo.CashLedgerRepository;
import com.example.cbs_mvp.repo.OrderRepository;

@Service
public class OrderService {

    private final OrderRepository orderRepo;
    private final CashLedgerRepository ledgerRepo;
    private final KillSwitchService killSwitch;
    private final StateTransitionService transitions;

    public OrderService(
            OrderRepository orderRepo,
            CashLedgerRepository ledgerRepo,
            KillSwitchService killSwitch,
            StateTransitionService transitions
    ) {
        this.orderRepo = orderRepo;
        this.ledgerRepo = ledgerRepo;
        this.killSwitch = killSwitch;
        this.transitions = transitions;
    }

    @Transactional
    public Order markDelivered(Long orderId) {
        guardNotPaused();
        Order order = getOrder(orderId);
        if ("DELIVERED".equals(order.getState())) {
            return order;
        }
        if (!"EBAY_TRACKING_UPLOADED".equals(order.getState())) {
            throw new IllegalStateException("order not ready: state=" + order.getState());
        }

        String from = order.getState();
        order.setState("DELIVERED");
        orderRepo.save(order);
        transitions.log("ORDER", orderId, from, order.getState(),
                "DELIVERED", null, "SYSTEM", cid());
        return order;
    }

    @Transactional
    public Order markClaim(Long orderId) {
        guardNotPaused();
        Order order = getOrder(orderId);
        if ("CLAIM".equals(order.getState())) {
            return order;
        }
        if (!"DELIVERED".equals(order.getState()) && !"EBAY_TRACKING_UPLOADED".equals(order.getState())) {
            throw new IllegalStateException("order not ready: state=" + order.getState());
        }

        String from = order.getState();
        order.setState("CLAIM");
        orderRepo.save(order);
        transitions.log("ORDER", orderId, from, order.getState(),
                "CLAIM", null, "SYSTEM", cid());
        return order;
    }

    @Transactional
    public CashLedger confirmSale(Long orderId) {
        guardNotPaused();
        Order order = getOrder(orderId);
        if (!"DELIVERED".equals(order.getState()) && !"CLAIM".equals(order.getState())) {
            throw new IllegalStateException("order not ready: state=" + order.getState());
        }
        BigDecimal amount = nz(order.getSoldPriceYen());

        CashLedger cl = ledgerRepo.findByRefTableAndRefIdAndEventType("orders", orderId, "SALE")
                .orElseGet(CashLedger::new);
        boolean created = cl.getCashId() == null;
        if (created) {
            cl.setRefTable("orders");
            cl.setRefId(orderId);
            cl.setEventType("SALE");
            cl.setAmountYen(amount);
            cl.setExpectedDate(null);
            cl.setCreatedAt(LocalDateTime.now());
        }
        if (cl.getActualDate() != null) {
            return cl;
        }
        cl.setActualDate(LocalDate.now());
        CashLedger saved = ledgerRepo.save(cl);
        transitions.log("ORDER", orderId, order.getState(), order.getState(),
                "SALE_CONFIRMED", created ? null : "ledger updated", "SYSTEM", cid());
        return saved;
    }

    @Transactional
    public CashLedger confirmRefund(Long orderId, BigDecimal amountYen) {
        guardNotPaused();
        Order order = getOrder(orderId);
        if (!"CLAIM".equals(order.getState())) {
            throw new IllegalStateException("order not ready: state=" + order.getState());
        }
        BigDecimal amount = nz(amountYen);
        if (amount.signum() > 0) {
            amount = amount.negate();
        }

        CashLedger cl = ledgerRepo.findByRefTableAndRefIdAndEventType("orders", orderId, "REFUND")
                .orElseGet(CashLedger::new);
        boolean created = cl.getCashId() == null;
        if (created) {
            cl.setRefTable("orders");
            cl.setRefId(orderId);
            cl.setEventType("REFUND");
            cl.setAmountYen(amount);
            cl.setExpectedDate(null);
            cl.setCreatedAt(LocalDateTime.now());
        }
        if (cl.getActualDate() != null) {
            return cl;
        }
        cl.setActualDate(LocalDate.now());
        CashLedger saved = ledgerRepo.save(cl);
        transitions.log("ORDER", orderId, order.getState(), order.getState(),
                "REFUND_CONFIRMED", created ? null : "ledger updated", "SYSTEM", cid());
        return saved;
    }

    private Order getOrder(Long orderId) {
        return orderRepo.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("order not found"));
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static String cid() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private void guardNotPaused() {
        if (killSwitch.isPaused()) {
            throw new IllegalStateException("system is paused");
        }
    }
}
