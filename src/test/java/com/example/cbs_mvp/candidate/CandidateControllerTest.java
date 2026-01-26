package com.example.cbs_mvp.candidate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import com.example.cbs_mvp.dto.BulkPricingRequest;
import com.example.cbs_mvp.repo.CandidateRepository;
import com.example.cbs_mvp.service.CandidateService;

class CandidateControllerTest {

    @Test
    void bulkPriceAndDraft_processesList() {
        CandidateService candidateService = mock(CandidateService.class);
        CandidateRepository candidateRepo = mock(CandidateRepository.class);
        CandidateController controller = new CandidateController(candidateService, candidateRepo);

        BulkPricingRequest req = new BulkPricingRequest();
        req.setCandidateIds(List.of(1L, 2L, 3L));
        req.setFxRate(new BigDecimal("150.0"));
        req.setAutoDraft(true);

        ResponseEntity<?> res = controller.bulkPriceAndDraft(req);

        assertEquals(200, res.getStatusCode().value());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) res.getBody();
        assertEquals(3, body.get("successCount"));
        assertEquals(0, body.get("failureCount"));

        verify(candidateService, times(1)).priceCandidate(1L, req.getFxRate(), null, true);
        verify(candidateService, times(1)).priceCandidate(2L, req.getFxRate(), null, true);
        verify(candidateService, times(1)).priceCandidate(3L, req.getFxRate(), null, true);
    }

    @Test
    void bulkPriceAndDraft_handlesFailures() {
        CandidateService candidateService = mock(CandidateService.class);
        CandidateRepository candidateRepo = mock(CandidateRepository.class);
        CandidateController controller = new CandidateController(candidateService, candidateRepo);

        BulkPricingRequest req = new BulkPricingRequest();
        req.setCandidateIds(List.of(1L, 2L));
        req.setFxRate(new BigDecimal("150.0"));

        // Fail for ID 2
        doThrow(new RuntimeException("Pricing failed")).when(candidateService).priceCandidate(eq(2L), any(), any(),
                anyBoolean());

        ResponseEntity<?> res = controller.bulkPriceAndDraft(req);

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) res.getBody();
        assertEquals(1, body.get("successCount"));
        assertEquals(1, body.get("failureCount"));

        @SuppressWarnings("unchecked")
        List<String> errors = (List<String>) body.get("errors");
        assertEquals(1, errors.size());
        assertEquals("ID 2: Pricing failed", errors.get(0));
    }
}
