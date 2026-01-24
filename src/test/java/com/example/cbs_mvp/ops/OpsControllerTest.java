package com.example.cbs_mvp.ops;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.math.BigDecimal;
import java.util.Map;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import com.example.cbs_mvp.repo.CandidateRepository;
import com.example.cbs_mvp.repo.PricingResultRepository;
import com.example.cbs_mvp.repo.PricingResultRepository.StatsSummary;
import com.example.cbs_mvp.service.StateTransitionService;
import com.example.cbs_mvp.repo.CashLedgerRepository;
import com.example.cbs_mvp.repo.EbayDraftRepository;
import com.example.cbs_mvp.repo.PurchaseOrderRepository;
import com.example.cbs_mvp.repo.StateTransitionRepository;

class OpsControllerTest {

    @Test
    void dashboardStats_returnsAggregatedData() {
        // Mocks
        OpsKeyService opsKeyService = mock(OpsKeyService.class);
        KillSwitchService killSwitchService = mock(KillSwitchService.class);
        SystemFlagService flags = mock(SystemFlagService.class);
        PurchaseOrderRepository poRepo = mock(PurchaseOrderRepository.class);
        CashLedgerRepository cashLedgerRepo = mock(CashLedgerRepository.class);
        EbayDraftRepository draftRepo = mock(EbayDraftRepository.class);
        StateTransitionService transitions = mock(StateTransitionService.class);
        StateTransitionRepository transitionRepo = mock(StateTransitionRepository.class);
        CandidateRepository candidateRepo = mock(CandidateRepository.class);
        PricingResultRepository pricingRepo = mock(PricingResultRepository.class);

        // Controller under test
        OpsController controller = new OpsController(
                opsKeyService, killSwitchService, flags, poRepo, cashLedgerRepo,
                draftRepo, transitions, transitionRepo, candidateRepo, pricingRepo);

        // Setup Mock Data
        when(opsKeyService.isValid("valid-key")).thenReturn(true);

        StatsSummary stats = mock(StatsSummary.class);
        when(stats.getAvgProfitRate()).thenReturn(new BigDecimal("0.2500"));
        when(stats.getTotalProfitYen()).thenReturn(new BigDecimal("15000"));
        when(stats.getTotalSalesYen()).thenReturn(new BigDecimal("60000"));

        when(pricingRepo.findStatsByState("DRAFT_READY")).thenReturn(stats);

        when(candidateRepo.countByState("DRAFT_READY")).thenReturn(5L);
        when(candidateRepo.countByStateIn(any())).thenReturn(20L); // 5 Draft, 15 others

        // Execute
        ResponseEntity<?> res = controller.dashboardStats("valid-key");

        // Verify
        assertEquals(200, res.getStatusCode().value());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) res.getBody();
        assertNotNull(body);

        assertEquals(new BigDecimal("0.2500"), body.get("avgProfitRate"));
        assertEquals(new BigDecimal("15000"), body.get("totalProfitYen"));
        assertEquals(5L, body.get("countDraftReady"));
        assertEquals(20L, body.get("countTotalScope"));
        assertEquals(0.25, (double) body.get("passRate"), 0.001); // 5/20 = 0.25
    }
}
