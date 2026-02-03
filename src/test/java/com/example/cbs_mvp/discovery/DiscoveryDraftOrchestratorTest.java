package com.example.cbs_mvp.discovery;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.cbs_mvp.ops.KillSwitchService;
import com.example.cbs_mvp.ops.SystemFlagService;
import com.example.cbs_mvp.service.CandidateService;
import com.example.cbs_mvp.service.DraftService;
import com.example.cbs_mvp.service.StateTransitionService;

@ExtendWith(MockitoExtension.class)
class DiscoveryDraftOrchestratorTest {

    @Mock
    private DiscoveryService discoveryService;
    @Mock
    private DiscoveryItemRepository discoveryRepo;
    @Mock
    private CandidateService candidateService;
    @Mock
    private DraftService draftService;
    @Mock
    private KillSwitchService killSwitch;
    @Mock
    private SystemFlagService systemFlagService;
    @Mock
    private StateTransitionService transitions;

    private DiscoveryDraftOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = new DiscoveryDraftOrchestrator(
                discoveryService,
                discoveryRepo,
                candidateService,
                draftService,
                killSwitch,
                systemFlagService,
                transitions);
    }

    @Test
    void createDraft_withFreshData_shouldPass() {
        // Arrange
        Long discoveryId = 1L;
        DiscoveryItem item = createTestItem(discoveryId, OffsetDateTime.now().minusHours(1));
        // refresh後はlastCheckedAtが更新されたitemを返す
        DiscoveryItem refreshedItem = createTestItem(discoveryId, OffsetDateTime.now());

        when(discoveryRepo.findById(discoveryId)).thenReturn(Optional.of(item));
        when(killSwitch.isPaused()).thenReturn(false);
        when(discoveryService.refresh(discoveryId)).thenReturn(refreshedItem);
        when(systemFlagService.get("DISCOVERY_MIN_SAFETY")).thenReturn("50");
        when(systemFlagService.get("DISCOVERY_FRESHNESS_REQUIRED_HOURS")).thenReturn("24");

        // Candidate/Draft作成のモック
        var candidate = new com.example.cbs_mvp.entity.Candidate();
        candidate.setCandidateId(100L);
        when(candidateService.createCandidate(any(), any(), any(), any(), any())).thenReturn(candidate);
        var pricingResult = new com.example.cbs_mvp.entity.PricingResult();
        pricingResult.setGateProfitOk(true);
        pricingResult.setGateCashOk(true);
        pricingResult.setProfitYen(BigDecimal.valueOf(5000));
        when(candidateService.priceCandidate(anyLong(), any(), any(), anyBoolean())).thenReturn(pricingResult);

        var draft = new com.example.cbs_mvp.entity.EbayDraft();
        draft.setDraftId(200L);
        when(draftService.createDraft(anyLong())).thenReturn(draft);

        // Act
        var result = orchestrator.createDraft(discoveryId, BigDecimal.valueOf(150), null);

        // Assert
        assertNotNull(result);
        assertEquals(200L, result.draftId());
    }

    @Test
    void createDraft_withStaleData_shouldThrowFreshnessTooOld() {
        // Arrange
        Long discoveryId = 2L;
        // 25時間前のデータ
        DiscoveryItem item = createTestItem(discoveryId, OffsetDateTime.now().minusHours(25));

        when(discoveryRepo.findById(discoveryId)).thenReturn(Optional.of(item));
        when(killSwitch.isPaused()).thenReturn(false);
        // refresh実行後もlastCheckedAtは更新されない（refreshが失敗したケースを想定）
        when(discoveryService.refresh(discoveryId)).thenReturn(item);
        when(systemFlagService.get("DISCOVERY_MIN_SAFETY")).thenReturn("50");
        when(systemFlagService.get("DISCOVERY_FRESHNESS_REQUIRED_HOURS")).thenReturn("24");

        // Act & Assert
        var ex = assertThrows(DiscoveryDraftOrchestrator.DraftConditionException.class, () -> {
            orchestrator.createDraft(discoveryId, BigDecimal.valueOf(150), null);
        });

        assertEquals("FRESHNESS_TOO_OLD", ex.getCode());
        assertTrue(ex.getMessage().contains("経過"));
    }

    @Test
    void createDraft_withNullLastCheckedAt_shouldThrowFreshnessTooOld() {
        // Arrange
        Long discoveryId = 3L;
        DiscoveryItem item = createTestItem(discoveryId, null);

        when(discoveryRepo.findById(discoveryId)).thenReturn(Optional.of(item));
        when(killSwitch.isPaused()).thenReturn(false);
        when(discoveryService.refresh(discoveryId)).thenReturn(item);
        when(systemFlagService.get("DISCOVERY_MIN_SAFETY")).thenReturn("50");
        // FRESHNESS_REQUIRED_HOURSは設定不要（lastCheckedAt==nullで先にエラー）

        // Act & Assert
        var ex = assertThrows(DiscoveryDraftOrchestrator.DraftConditionException.class, () -> {
            orchestrator.createDraft(discoveryId, BigDecimal.valueOf(150), null);
        });

        assertEquals("FRESHNESS_TOO_OLD", ex.getCode());
        assertTrue(ex.getMessage().contains("null"));
    }

    @Test
    void createDraft_withCustomFreshnessHours_shouldRespectConfig() {
        // Arrange
        Long discoveryId = 4L;
        // 5時間前のデータ
        DiscoveryItem item = createTestItem(discoveryId, OffsetDateTime.now().minusHours(5));

        when(discoveryRepo.findById(discoveryId)).thenReturn(Optional.of(item));
        when(killSwitch.isPaused()).thenReturn(false);
        when(discoveryService.refresh(discoveryId)).thenReturn(item);
        when(systemFlagService.get("DISCOVERY_MIN_SAFETY")).thenReturn("50");
        // 4時間以内を要求
        when(systemFlagService.get("DISCOVERY_FRESHNESS_REQUIRED_HOURS")).thenReturn("4");

        // Act & Assert
        var ex = assertThrows(DiscoveryDraftOrchestrator.DraftConditionException.class, () -> {
            orchestrator.createDraft(discoveryId, BigDecimal.valueOf(150), null);
        });

        assertEquals("FRESHNESS_TOO_OLD", ex.getCode());
    }

    @Test
    void createDraft_idempotency_shouldReturnExistingDraft() {
        // Arrange
        Long discoveryId = 5L;
        DiscoveryItem item = createTestItem(discoveryId, OffsetDateTime.now());
        item.setLinkedDraftId(999L); // 既にDraft済み

        when(discoveryRepo.findById(discoveryId)).thenReturn(Optional.of(item));
        // System Flagsなどは今回は呼ばれる前に戻るはずだが、もし呼ばれたときのためにモック
        // when(killSwitch.isPaused()).thenReturn(false);

        // Act
        var result = orchestrator.createDraft(discoveryId, BigDecimal.valueOf(150), null);

        // Assert
        assertNotNull(result);
        assertEquals(999L, result.draftId());
        assertEquals("ALREADY_DRAFTED", result.status());
    }

    private DiscoveryItem createTestItem(Long id, OffsetDateTime lastCheckedAt) {
        DiscoveryItem item = new DiscoveryItem();
        item.setId(id);
        item.setSourceUrl("https://example.com/item" + id);
        item.setPriceYen(BigDecimal.valueOf(10000));
        item.setSafetyScore(80);
        item.setProfitScore(60);
        item.setOverallScore(70);
        item.setStatus("CHECKED");
        item.setCondition("NEW");
        item.setCategoryHint("Electronics");
        item.setLastCheckedAt(lastCheckedAt);
        return item;
    }
}
