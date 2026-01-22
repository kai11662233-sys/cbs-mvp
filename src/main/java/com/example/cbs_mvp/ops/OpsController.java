package com.example.cbs_mvp.ops;

import com.example.cbs_mvp.repo.CashLedgerRepository;
import com.example.cbs_mvp.repo.EbayDraftRepository;
import com.example.cbs_mvp.repo.PurchaseOrderRepository;
import com.example.cbs_mvp.repo.StateTransitionRepository;
import com.example.cbs_mvp.service.StateTransitionService;
import com.example.cbs_mvp.entity.StateTransition;
import jakarta.annotation.PostConstruct;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/ops")
public class OpsController {

    private final OpsKeyService opsKeyService;
    private final KillSwitchService killSwitchService;
    private final SystemFlagService flags;
    private final PurchaseOrderRepository poRepo;
    private final CashLedgerRepository cashLedgerRepo;
    private final EbayDraftRepository draftRepo;
    private final StateTransitionService transitions;
    private final StateTransitionRepository transitionRepo;

    public OpsController(
            OpsKeyService opsKeyService,
            KillSwitchService killSwitchService,
            SystemFlagService flags,
            PurchaseOrderRepository poRepo,
            CashLedgerRepository cashLedgerRepo,
            EbayDraftRepository draftRepo,
            StateTransitionService transitions,
            StateTransitionRepository transitionRepo) {
        this.opsKeyService = opsKeyService;
        this.killSwitchService = killSwitchService;
        this.flags = flags;
        this.poRepo = poRepo;
        this.cashLedgerRepo = cashLedgerRepo;
        this.draftRepo = draftRepo;
        this.transitions = transitions;
        this.transitionRepo = transitionRepo;
    }

    @PostConstruct
    void initOpsKey() {
        opsKeyService.ensureDefaultOpsKeyIfMissing("dev-ops-key");
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        boolean paused = killSwitchService.isPaused();
        String reason = killSwitchService.getReason();
        LocalDateTime updatedAt = killSwitchService.getUpdatedAt();
        return Map.of(
                "paused", paused,
                "reason", reason,
                "updatedAt", updatedAt);
    }

    @PostMapping("/pause")
    public ResponseEntity<?> pause(
            @RequestHeader(value = "X-OPS-KEY", required = false) String opsKey,
            @RequestBody(required = false) Map<String, Object> body) {
        if (!opsKeyService.isValid(opsKey)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "invalid ops key"));
        }
        String reason = body == null ? "" : String.valueOf(body.getOrDefault("reason", ""));
        killSwitchService.pause(reason);
        return ResponseEntity.ok(Map.of("paused", true, "reason", reason));
    }

    @PostMapping("/resume")
    public ResponseEntity<?> resume(@RequestHeader(value = "X-OPS-KEY", required = false) String opsKey) {
        if (!opsKeyService.isValid(opsKey)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "invalid ops key"));
        }
        killSwitchService.resume();
        return ResponseEntity.ok(Map.of("paused", false));
    }

    /**
     * サマリー情報取得
     * OPS-KEY または JWT認証で利用可能
     */
    @GetMapping("/summary")
    public ResponseEntity<?> summary(
            @RequestHeader(value = "X-OPS-KEY", required = false) String opsKey) {

        // OPS-KEY または SecurityContextの認証状態をチェック
        boolean isOpsKeyValid = opsKeyService.isValid(opsKey);
        boolean isAuthenticated = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication() != null
                && org.springframework.security.core.context.SecurityContextHolder
                        .getContext().getAuthentication().isAuthenticated()
                && !(org.springframework.security.core.context.SecurityContextHolder
                        .getContext()
                        .getAuthentication() instanceof org.springframework.security.authentication.AnonymousAuthenticationToken);

        if (!isOpsKeyValid && !isAuthenticated) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "X-OPS-KEY or JWT required"));
        }

        BigDecimal openCommitments = nz(poRepo.calculateOpenCommitments());
        BigDecimal sales30dYen = nz(sumSales30d());
        BigDecimal sales30dFlagYen = bd(flags.get("RECENT_SALES_30D"), "0");

        int draftFailedLast10 = countDraftFailedLast10();
        int poFailedLast10 = countPoFailedLast10();
        int trackingFailedLast10 = countTrackingFailedLast10();
        int lastFailureCount = draftFailedLast10 + poFailedLast10 + trackingFailedLast10;

        return ResponseEntity.ok(Map.ofEntries(
                Map.entry("paused", killSwitchService.isPaused()),
                Map.entry("pauseReason", killSwitchService.getReason()),
                Map.entry("pauseUpdatedAt", killSwitchService.getUpdatedAt()),
                Map.entry("openCommitmentsYen", openCommitments),
                Map.entry("sales30dYen", sales30dYen),
                Map.entry("sales30dFlagYen", sales30dFlagYen),
                Map.entry("draftFailedLast10", draftFailedLast10),
                Map.entry("poFailedLast10", poFailedLast10),
                Map.entry("trackingFailedLast10", trackingFailedLast10),
                Map.entry("lastFailureCount", lastFailureCount),
                Map.entry("ts", Instant.now().toString())));
    }

    @PostMapping("/recalc-sales-30d")
    public ResponseEntity<?> recalcSales30d(@RequestHeader(value = "X-OPS-KEY", required = false) String opsKey) {
        if (!opsKeyService.isValid(opsKey)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "invalid ops key"));
        }

        BigDecimal sales30dYen = nz(sumSales30d());
        flags.set("RECENT_SALES_30D", sales30dYen.toPlainString());

        transitions.log(
                "SYSTEM",
                0L,
                null,
                "RECENT_SALES_30D_RECALC",
                "RECENT_SALES_30D_RECALC",
                "value=" + sales30dYen,
                "SYSTEM",
                cid());

        return ResponseEntity.ok(Map.of(
                "sales30dYen", sales30dYen,
                "updatedFlagYen", sales30dYen,
                "ts", Instant.now().toString()));
    }

    private BigDecimal sumSales30d() {
        LocalDate fromDate = LocalDate.now().minusDays(30);
        return cashLedgerRepo.sumAmountByEventTypeSince("SALE", fromDate);
    }

    private int countDraftFailedLast10() {
        var recentDrafts = draftRepo.findRecentByCreatedAt(PageRequest.of(0, 10));
        return (int) recentDrafts.stream()
                .filter(draft -> "EBAY_DRAFT_FAILED".equals(draft.getState()))
                .count();
    }

    private int countPoFailedLast10() {
        var recentPos = poRepo.findRecent(PageRequest.of(0, 10));
        return (int) recentPos.stream()
                .filter(po -> "PROCUREMENT_FAILED".equals(po.getState()))
                .count();
    }

    private int countTrackingFailedLast10() {
        var recent = transitionRepo.findRecentByEntityTypeAndReasonCode(
                "ORDER",
                "EBAY_TRACKING_UPLOAD_FAILED",
                PageRequest.of(0, 10));
        return (int) recent.stream()
                .map(StateTransition::getEntityId)
                .distinct()
                .count();
    }

    private static String cid() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static BigDecimal bd(String s, String def) {
        String v = (s == null || s.isBlank()) ? def : s.trim();
        return new BigDecimal(v);
    }
}
