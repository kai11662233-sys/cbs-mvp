package com.example.cbs_mvp.candidate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.cbs_mvp.entity.Candidate;
import com.example.cbs_mvp.fx.FxRateService;
import com.example.cbs_mvp.repo.CandidateRepository;
import com.example.cbs_mvp.service.CandidateService;
import com.example.cbs_mvp.service.StateTransitionService;

class CandidateCsvImportServiceTest {

    private CandidateRepository candidateRepo;
    private StateTransitionService transitions;
    private CandidateService candidateService;
    private FxRateService fxRateService;
    private CandidateCsvImportService importService;

    @BeforeEach
    void setUp() {
        candidateRepo = mock(CandidateRepository.class);
        transitions = mock(StateTransitionService.class);
        candidateService = mock(CandidateService.class);
        fxRateService = mock(FxRateService.class);

        importService = new CandidateCsvImportService(candidateRepo, transitions, candidateService, fxRateService);
    }

    @Test
    void importFromCsv_withoutAutoFilter_savesCandidateOnly() {
        String csv = "sourceUrl,sourcePriceYen,weightKg,sizeTier\n" +
                "http://example.com/1,1000,1.5,M";
        InputStream is = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));

        when(candidateRepo.findBySourceUrl(any())).thenReturn(Optional.empty());
        when(candidateRepo.save(any(Candidate.class))).thenAnswer(inv -> inv.getArgument(0));

        var result = importService.importFromCsv(is, false, false);

        assertEquals(1, result.successCount());
        assertEquals(0, result.errorCount());
        verify(candidateRepo, times(1)).save(any(Candidate.class));
        verify(candidateService, never()).priceCandidate(any(), any(), any(), anyBoolean());
    }

    @Test
    void importFromCsv_withAutoFilter_callsPriceCandidate() {
        String csv = "sourceUrl,sourcePriceYen\n" +
                "http://example.com/2,2000";
        InputStream is = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));

        when(candidateRepo.findBySourceUrl(any())).thenReturn(Optional.empty());
        Candidate savedCandidate = new Candidate();
        savedCandidate.setCandidateId(10L);
        when(candidateRepo.save(any(Candidate.class))).thenReturn(savedCandidate);

        // Mock FX
        when(fxRateService.getCurrentRate())
                .thenReturn(new FxRateService.FxRateResult(new BigDecimal("150"), null, null));

        var result = importService.importFromCsv(is, false, true);

        assertEquals(1, result.successCount());
        verify(candidateRepo, times(1)).save(any(Candidate.class));
        verify(candidateService, times(1)).priceCandidate(eq(10L), eq(new BigDecimal("150")), eq(null), eq(false));
    }

    @Test
    void importFromCsv_withAutoFilter_failsIfNoFx() {
        String csv = "sourceUrl,sourcePriceYen\n" +
                "http://example.com/3,3000";
        InputStream is = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));

        // Mock FX Error
        when(fxRateService.getCurrentRate()).thenReturn(new FxRateService.FxRateResult(null, null, "API Error"));

        var result = importService.importFromCsv(is, false, true);

        // Should return error result immediately
        assertEquals(0, result.successCount());
        assertEquals(1, result.errorCount());
        assertTrue(result.errors().get(0).contains("failed to get FX rate"));
        verify(candidateRepo, never()).save(any());
    }
}
