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

    @PostMapping("/calc")
    public ResponseEntity<PricingResponse> calc(@Valid @RequestBody PricingRequest req) {
        return ResponseEntity.ok(calculator.calculate(req));
    }
}
