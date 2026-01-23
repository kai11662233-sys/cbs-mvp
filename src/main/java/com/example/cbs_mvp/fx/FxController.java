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
public class FxController {

    private final FxRateService fxRateService;

    /**
     * 現在の為替レートを取得
     */
    @GetMapping("/rate")
    public ResponseEntity<?> getRate() {
        org.slf4j.LoggerFactory.getLogger(FxController.class).info("Received request for /fx/rate");
        FxRateService.FxRateResult result = fxRateService.getCurrentRate();

        if (result.isSuccess()) {
            return ResponseEntity.ok(Map.of(
                    "rate", result.rate(),
                    "updatedAt", result.updatedAt() != null ? result.updatedAt().toString() : null));
        } else {
            java.util.Map<String, Object> response = new java.util.HashMap<>();
            response.put("error", result.error());
            response.put("rate", null);
            return ResponseEntity.ok(response);
        }
    }

    /**
     * 為替レートを手動で更新
     */
    @PostMapping("/refresh")
    public ResponseEntity<?> refreshRate() {
        FxRateService.FxRateResult result = fxRateService.refreshRate();

        if (result.isSuccess()) {
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "rate", result.rate(),
                    "updatedAt", result.updatedAt() != null ? result.updatedAt().toString() : null));
        } else {
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "error", result.error()));
        }
    }
}
