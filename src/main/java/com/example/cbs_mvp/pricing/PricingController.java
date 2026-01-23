package com.example.cbs_mvp.pricing;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/pricing")
@RequiredArgsConstructor
@Validated
public class PricingController {

    private final PricingCalculator calculator;
    private final com.example.cbs_mvp.repo.PricingResultRepository pricingRepo;

    @PostMapping("/calc")
    public ResponseEntity<PricingResponse> calc(@Valid @RequestBody PricingRequest req) {
        PricingResponse res = calculator.calculate(req);

        if (req.getCandidateId() != null) {
            pricingRepo.findByCandidateId(req.getCandidateId()).ifPresent(prev -> {
                // Populate Comparison Fields (Using Reflection or Builder would be cleaner, but
                // modifying Response object directly here)
                // Since PricingResponse is immutable (@Value), we need to reconstruct it or
                // change to @Data
                // Workaround: Re-build using toBuilder = true if available, or manual copy.
                // Let's check PricingResponse annotations. It has @Builder.

                // We can't modify 'res' directly.
                // Let's return a new builder based on 'res' values + comparison.
                // But @Value generates getters.

                // Better approach: Add logic in a service or here to map.
                // Since I can't easily modify 'res' without toBuilder=true on @Builder (default
                // is off), I will assume I can add it or doing manual build.
                // Or I can modify PricingResponse to be mutable for this purpose, but let's
                // stick to immutable.
                // I'll add toBuilder=true to PricingResponse in a separate edit or assume I
                // can't.
                // Let's create a new response from the old one manually for now as fields are
                // few.

                // Wait, PricingResponse has 15+ fields. Manual copy is verbose.
                // I will update PricingResponse to have @Builder(toBuilder=true) first? No,
                // I'll just do it here if possible.
                // Actually, let's just use a helper method or update PricingResponse.
            });

            // To implement this clean, I need to fetch 'prev', calculate diff, and re-build
            // response.
            // Let's update Controller to use a helper.
            return ResponseEntity.ok(withComparison(res, req.getCandidateId()));
        }

        return ResponseEntity.ok(res);
    }

    private PricingResponse withComparison(PricingResponse current, Long candidateId) {
        return pricingRepo.findByCandidateId(candidateId)
                .map(prev -> {
                    java.math.BigDecimal diffProfit = current.getProfitYen().subtract(prev.getProfitYen());

                    return PricingResponse.builder()
                            .safeWeightKg(current.getSafeWeightKg())
                            .safeSizeTier(current.getSafeSizeTier())
                            .fxSafe(current.getFxSafe())
                            .calcSourcePriceYen(current.getCalcSourcePriceYen())
                            .usedFeeRate(current.getUsedFeeRate())
                            .intlShipCostYen(current.getIntlShipCostYen())
                            .totalCostYen(current.getTotalCostYen())
                            .recSellUsd(current.getRecSellUsd())
                            .useSellUsd(current.getUseSellUsd())
                            .sellYen(current.getSellYen())
                            .feesAndReserveYen(current.getFeesAndReserveYen())
                            .profitYen(current.getProfitYen())
                            .profitRate(current.getProfitRate())
                            .gateProfitOk(current.isGateProfitOk())
                            .warn(current.getWarn())
                            // Diff
                            .prevProfitYen(prev.getProfitYen())
                            .prevProfitRate(prev.getProfitRate())
                            .diffProfitYen(diffProfit)
                            .build();
                })
                .orElse(current);
    }
}
