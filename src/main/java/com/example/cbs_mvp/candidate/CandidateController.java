package com.example.cbs_mvp.candidate;

import java.util.List;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.cbs_mvp.dto.BulkPricingRequest;
import com.example.cbs_mvp.dto.CandidatePricingRequest;
import com.example.cbs_mvp.dto.CreateCandidateRequest;
import com.example.cbs_mvp.entity.Candidate;
import com.example.cbs_mvp.entity.PricingResult;
import com.example.cbs_mvp.repo.CandidateRepository;
import com.example.cbs_mvp.service.CandidateService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/candidates")
public class CandidateController {

    private final CandidateService candidateService;
    private final CandidateRepository candidateRepo;

    public CandidateController(CandidateService candidateService, CandidateRepository candidateRepo) {
        this.candidateService = candidateService;
        this.candidateRepo = candidateRepo;
    }

    /**
     * Candidate一覧取得（直近50件）
     */
    @GetMapping
    public ResponseEntity<List<Candidate>> list(
            @RequestParam(value = "limit", defaultValue = "50") int limit) {
        var pageable = PageRequest.of(0, Math.min(limit, 100), Sort.by(Sort.Direction.DESC, "candidateId"));
        var candidates = candidateRepo.findAll(pageable).getContent();
        return ResponseEntity.ok(candidates);
    }

    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody CreateCandidateRequest req) {
        Candidate c = candidateService.createCandidate(
                req.getSourceUrl(),
                req.getSourcePriceYen(),
                req.getWeightKg(),
                req.getSizeTier(),
                req.getMemo());
        return ResponseEntity.ok(Map.of(
                "candidateId", c.getCandidateId(),
                "state", c.getState()));
    }

    @PostMapping("/{candidateId}/pricing")
    public ResponseEntity<?> pricing(@PathVariable Long candidateId, @Valid @RequestBody CandidatePricingRequest req) {
        PricingResult pr = candidateService.priceCandidate(
                candidateId,
                req.getFxRate(),
                req.getTargetSellUsd(),
                req.getAutoDraft() != null && req.getAutoDraft() // Default false
        );
        return ResponseEntity.ok(Map.of(
                "candidateId", pr.getCandidateId(),
                "gateProfitOk", pr.isGateProfitOk(),
                "gateCashOk", pr.isGateCashOk(),
                "totalCostYen", pr.getTotalCostYen(),
                "sellPriceUsd", pr.getSellPriceUsd(),
                "sellPriceYen", pr.getSellPriceYen()));
    }

    @PostMapping("/bulk/price-and-draft")
    public ResponseEntity<?> bulkPriceAndDraft(@Valid @RequestBody BulkPricingRequest req) {
        int successCount = 0;
        int failureCount = 0;
        java.util.List<String> errors = new java.util.ArrayList<>();

        for (Long id : req.getCandidateIds()) {
            try {
                // Auto-draft if requested
                candidateService.priceCandidate(id, req.getFxRate(), null,
                        req.getAutoDraft() != null && req.getAutoDraft());
                successCount++;
            } catch (Exception e) {
                failureCount++;
                errors.add("ID " + id + ": " + e.getMessage());
            }
        }

        return ResponseEntity.ok(Map.of(
                "successCount", successCount,
                "failureCount", failureCount,
                "errors", errors));
    }
}
