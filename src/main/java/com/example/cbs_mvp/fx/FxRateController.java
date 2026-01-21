package com.example.cbs_mvp.fx;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/fx")
@RequiredArgsConstructor
public class FxRateController {

    private final FxRateService fxRateService;

    /**
     * 現在の為替レートを取得
     */
    @GetMapping("/rate")
    public ResponseEntity<?> getRate() {
        FxRateService.FxRateResult result = fxRateService.getCurrentRate();

        if (!result.isSuccess()) {
            return ResponseEntity.ok(Map.of(
                    "rate", (Object) null,
                    "error", result.error()));
        }

        return ResponseEntity.ok(Map.of(
                "rate", result.rate(),
                "updatedAt", result.updatedAt() != null ? result.updatedAt().toString() : null));
    }

    /**
     * 為替レートを手動で更新
     */
    @PostMapping("/refresh")
    public ResponseEntity<?> refreshRate() {
        FxRateService.FxRateResult result = fxRateService.refreshRate();

        if (!result.isSuccess()) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", result.error()));
        }

        return ResponseEntity.ok(Map.of(
                "rate", result.rate(),
                "updatedAt", result.updatedAt().toString(),
                "message", "FX rate updated successfully"));
    }
}
