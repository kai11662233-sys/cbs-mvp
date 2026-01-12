package com.example.cbs_mvp.candidate;

import java.math.BigDecimal;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.cbs_mvp.entity.Candidate;
import com.example.cbs_mvp.entity.PricingResult;
import com.example.cbs_mvp.service.CandidateService;

@RestController
@RequestMapping("/candidates")
public class CandidateController {

    private final CandidateService candidateService;

    public CandidateController(CandidateService candidateService) {
        this.candidateService = candidateService;
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody CreateCandidateRequest req) {
        if (req == null || req.sourceUrl == null || req.sourceUrl.isBlank() || req.sourcePriceYen == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "sourceUrl and sourcePriceYen are required"));
        }
        Candidate c = candidateService.createCandidate(
                req.sourceUrl,
                req.sourcePriceYen,
                req.weightKg,
                req.sizeTier,
                req.memo
        );
        return ResponseEntity.ok(Map.of(
                "candidateId", c.getCandidateId(),
                "state", c.getState()
        ));
    }

    @PostMapping("/{candidateId}/pricing")
    public ResponseEntity<?> pricing(@PathVariable Long candidateId, @RequestBody CandidatePricingRequest req) {
        if (req == null || req.fxRate == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "fxRate is required"));
        }
        try {
            PricingResult pr = candidateService.priceCandidate(candidateId, req.fxRate, req.targetSellUsd);
            return ResponseEntity.ok(Map.of(
                    "candidateId", pr.getCandidateId(),
                    "gateProfitOk", pr.isGateProfitOk(),
                    "gateCashOk", pr.isGateCashOk(),
                    "totalCostYen", pr.getTotalCostYen(),
                    "sellPriceUsd", pr.getSellPriceUsd(),
                    "sellPriceYen", pr.getSellPriceYen()
            ));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", ex.getMessage()));
        }
    }

    public static class CreateCandidateRequest {
        public String sourceUrl;
        public BigDecimal sourcePriceYen;
        public BigDecimal weightKg;
        public String sizeTier;
        public String memo;
    }

    public static class CandidatePricingRequest {
        public BigDecimal fxRate;
        public BigDecimal targetSellUsd;
    }
}
