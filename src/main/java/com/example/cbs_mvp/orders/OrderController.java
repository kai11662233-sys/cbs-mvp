package com.example.cbs_mvp.orders;

import java.math.BigDecimal;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.cbs_mvp.entity.CashLedger;
import com.example.cbs_mvp.entity.Order;
import com.example.cbs_mvp.service.OrderImportService;
import com.example.cbs_mvp.service.OrderService;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService service;
    private final OrderImportService importService;

    public OrderController(OrderService service, OrderImportService importService) {
        this.service = service;
        this.importService = importService;
    }

    @PostMapping("/{orderId}/mark-delivered")
    public ResponseEntity<?> markDelivered(@PathVariable Long orderId) {
        try {
            Order o = service.markDelivered(orderId);
            return ResponseEntity.ok(Map.of("orderId", o.getOrderId(), "state", o.getState()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping("/{orderId}/confirm-sale")
    public ResponseEntity<?> confirmSale(@PathVariable Long orderId) {
        try {
            CashLedger cl = service.confirmSale(orderId);
            return ResponseEntity.ok(Map.of("orderId", orderId, "cashId", cl.getCashId()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping("/{orderId}/mark-claim")
    public ResponseEntity<?> markClaim(@PathVariable Long orderId) {
        try {
            Order o = service.markClaim(orderId);
            return ResponseEntity.ok(Map.of("orderId", o.getOrderId(), "state", o.getState()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping("/{orderId}/confirm-refund")
    public ResponseEntity<?> confirmRefund(@PathVariable Long orderId, @RequestBody RefundRequest req) {
        try {
            if (req.amountYen == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "amountYen is required"));
            }
            CashLedger cl = service.confirmRefund(orderId, req.amountYen);
            return ResponseEntity.ok(Map.of("orderId", orderId, "cashId", cl.getCashId()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping("/sold")
    public ResponseEntity<?> importSold(@RequestBody SoldImportRequest req) {
        try {
            if (req == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "request body is required"));
            }
            var result = importService.importSold(new OrderImportService.SoldImportCommand(
                    req.ebayOrderKey,
                    req.draftId,
                    req.soldPriceUsd,
                    req.fxRate
            ));
            return ResponseEntity.ok(Map.of(
                    "orderId", result.order().getOrderId(),
                    "state", result.order().getState(),
                    "ebayOrderKey", result.order().getEbayOrderKey(),
                    "idempotent", result.idempotent()
            ));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
        }
    }

    public static class RefundRequest {
        public BigDecimal amountYen;
    }

    public static class SoldImportRequest {
        public String ebayOrderKey;
        public Long draftId;
        public BigDecimal soldPriceUsd;
        public BigDecimal fxRate;
    }
}
