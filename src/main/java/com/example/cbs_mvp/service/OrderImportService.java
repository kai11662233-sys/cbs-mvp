package com.example.cbs_mvp.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.cbs_mvp.entity.Order;
import com.example.cbs_mvp.ops.KillSwitchService;
import com.example.cbs_mvp.repo.EbayDraftRepository;
import com.example.cbs_mvp.repo.OrderRepository;

@Service
public class OrderImportService {

    private final OrderRepository orderRepo;
    private final EbayDraftRepository draftRepo;
    private final KillSwitchService killSwitch;
    private final StateTransitionService transitions;

    public OrderImportService(
            OrderRepository orderRepo,
            EbayDraftRepository draftRepo,
            KillSwitchService killSwitch,
            StateTransitionService transitions
    ) {
        this.orderRepo = orderRepo;
        this.draftRepo = draftRepo;
        this.killSwitch = killSwitch;
        this.transitions = transitions;
    }

    @Transactional
    public SoldImportResult importSold(SoldImportCommand cmd) {
        if (killSwitch.isPaused()) {
            throw new IllegalStateException("system is paused");
        }
        if (cmd.ebayOrderKey() == null || cmd.ebayOrderKey().isBlank()) {
            throw new IllegalArgumentException("ebayOrderKey is required");
        }
        if (cmd.soldPriceUsd() == null) {
            throw new IllegalArgumentException("soldPriceUsd is required");
        }
        if (cmd.soldPriceUsd().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("soldPriceUsd must be > 0");
        }
        if (cmd.fxRate() == null) {
            throw new IllegalArgumentException("fxRate is required");
        }
        if (cmd.fxRate().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("fxRate must be > 0");
        }

        if (cmd.draftId() != null && !draftRepo.existsById(cmd.draftId())) {
            throw new IllegalArgumentException("draftId not found");
        }

        Order existing = orderRepo.findByEbayOrderKey(cmd.ebayOrderKey()).orElse(null);
        if (existing != null) {
            return new SoldImportResult(existing, true);
        }

        Order order = new Order();
        order.setEbayOrderKey(cmd.ebayOrderKey());
        order.setDraftId(cmd.draftId());
        order.setSoldPriceUsd(cmd.soldPriceUsd());
        order.setSoldPriceYen(calcYen(cmd.soldPriceUsd(), cmd.fxRate()));
        order.setState("SOLD");
        order = orderRepo.save(order);

        transitions.log("ORDER", order.getOrderId(), null, "SOLD", "CREATE_SOLD",
                null, "SYSTEM", cid());
        return new SoldImportResult(order, false);
    }

    private static BigDecimal calcYen(BigDecimal usd, BigDecimal fx) {
        return usd.multiply(fx).setScale(2, RoundingMode.HALF_UP);
    }

    private static String cid() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public record SoldImportCommand(
            String ebayOrderKey,
            Long draftId,
            BigDecimal soldPriceUsd,
            BigDecimal fxRate
    ) {}

    public record SoldImportResult(Order order, boolean idempotent) {}
}
