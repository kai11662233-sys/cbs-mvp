package com.example.cbs_mvp.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.cbs_mvp.entity.Fulfillment;
import com.example.cbs_mvp.entity.Order;
import com.example.cbs_mvp.entity.PurchaseOrder;
import com.example.cbs_mvp.ops.KillSwitchService;
import com.example.cbs_mvp.repo.FulfillmentRepository;
import com.example.cbs_mvp.repo.OrderRepository;
import com.example.cbs_mvp.repo.PurchaseOrderRepository;

@Service
public class ThreePlService {

    private final PurchaseOrderRepository poRepo;
    private final OrderRepository orderRepo;
    private final FulfillmentRepository fulfillmentRepo;
    private final KillSwitchService killSwitch;
    private final StateTransitionService transitions;

    public ThreePlService(
            PurchaseOrderRepository poRepo,
            OrderRepository orderRepo,
            FulfillmentRepository fulfillmentRepo,
            KillSwitchService killSwitch,
            StateTransitionService transitions
    ) {
        this.poRepo = poRepo;
        this.orderRepo = orderRepo;
        this.fulfillmentRepo = fulfillmentRepo;
        this.killSwitch = killSwitch;
        this.transitions = transitions;
    }

    @Transactional
    public String exportRequestedPos(int limit) {
        if (killSwitch.isPaused()) {
            throw new IllegalStateException("system is paused");
        }

        List<PurchaseOrder> pos = poRepo.findByState("REQUESTED", PageRequest.of(0, limit));
        StringBuilder sb = new StringBuilder();
        sb.append("po_id,order_id,ship_to_3pl_address,inbound_tracking,expected_total_cost_yen,notes\n");

        for (PurchaseOrder po : pos) {
            String notes = "order_id=" + po.getOrderId() + ";po_id=" + po.getPoId();
            sb.append(csv(po.getPoId()))
              .append(",")
              .append(csv(po.getOrderId()))
              .append(",")
              .append(csv(po.getShipTo3plAddress()))
              .append(",")
              .append(csv(po.getInboundTracking()))
              .append(",")
              .append(csv(po.getExpectedTotalCostYen()))
              .append(",")
              .append(csv(notes))
              .append("\n");

            String from = po.getState();
            po.setState("SHIPPED_TO_3PL");
            poRepo.save(po);
            transitions.log("PO", po.getPoId(), from, po.getState(), "EXPORT_3PL", null, "SYSTEM", cid());
        }

        return sb.toString();
    }

    public ImportResult importTrackingCsv(String csvBody) {
        if (killSwitch.isPaused()) {
            throw new IllegalStateException("system is paused");
        }
        if (csvBody == null || csvBody.isBlank()) {
            return new ImportResult(0, 0, 0);
        }

        String[] lines = csvBody.split("\\r?\\n");
        int idx = 0;
        Map<String, Integer> header = new HashMap<>();
        if (lines.length > 0 && lines[0].toLowerCase().contains("order_id")) {
            String[] cols = lines[0].split(",", -1);
            for (int i = 0; i < cols.length; i++) {
                header.put(cols[i].trim().toLowerCase(), i);
            }
            idx = 1;
        }

        int updated = 0;
        int skipped = 0;
        int errors = 0;

        for (; idx < lines.length; idx++) {
            String line = lines[idx].trim();
            if (line.isEmpty()) continue;
            String[] cols = line.split(",", -1);

            String orderIdStr = get(cols, header, "order_id", 0);
            String carrier = get(cols, header, "outbound_carrier", 1);
            String tracking = get(cols, header, "outbound_tracking", 2);

            if (orderIdStr == null || orderIdStr.isBlank() || tracking == null || tracking.isBlank()) {
                skipped++;
                continue;
            }

            Long orderId;
            try {
                orderId = Long.parseLong(orderIdStr.trim());
            } catch (NumberFormatException ex) {
                errors++;
                continue;
            }

            Order order = orderRepo.findById(orderId).orElse(null);
            if (order == null) {
                errors++;
                continue;
            }

            Fulfillment f = fulfillmentRepo.findByOrderId(orderId).orElseGet(Fulfillment::new);
            f.setOrderId(orderId);
            f.setOutboundCarrier(blankToNull(carrier));
            f.setOutboundTracking(tracking);
            f.setState("3PL_SHIPPED_INTL");
            fulfillmentRepo.save(f);

            String from = order.getState();
            order.setState("3PL_SHIPPED_INTL");
            orderRepo.save(order);
            transitions.log("ORDER", orderId, from, order.getState(), "TRACKING_IMPORTED", null, "SYSTEM", cid());

            updated++;
        }

        return new ImportResult(updated, skipped, errors);
    }

    public record ImportResult(int updated, int skipped, int errors) {}

    private static String get(String[] cols, Map<String, Integer> header, String key, int fallbackIndex) {
        Integer idx = header.get(key);
        int i = (idx == null) ? fallbackIndex : idx;
        return i >= 0 && i < cols.length ? cols[i].trim() : null;
    }

    private static String blankToNull(String v) {
        return (v == null || v.isBlank()) ? null : v.trim();
    }

    private static String csv(Object v) {
        if (v == null) return "";
        String s = String.valueOf(v);
        boolean needsQuote = s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r");
        if (needsQuote) {
            s = s.replace("\"", "\"\"");
            return "\"" + s + "\"";
        }
        return s;
    }

    private static String cid() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
