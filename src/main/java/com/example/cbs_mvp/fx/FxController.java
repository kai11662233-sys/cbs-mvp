package com.example.cbs_mvp.fx;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.cbs_mvp.fx.FxRateService.FxRateResult;

import lombok.RequiredArgsConstructor;

/**
 * 為替レート管理コントローラ
 * 
 * フロントエンドから呼び出される為替レート関連のエンドポイントを提供
 */
@RestController
@RequestMapping("/fx")
@RequiredArgsConstructor
public class FxController {

    private final FxRateService fxRateService;

    /**
     * 現在の為替レートを取得
     * 
     * レートが未設定の場合はデフォルト値(150.00)を返す
     */
    @GetMapping("/rate")
    public ResponseEntity<?> getRate() {
        FxRateResult result = fxRateService.getCurrentRate();

        if (result.isSuccess()) {
            return ResponseEntity.ok(Map.of(
                    "rate", result.rate(),
                    "updatedAt", result.updatedAt() != null ? result.updatedAt().toString() : null));
        }

        // レートが未設定の場合はデフォルト値を返す
        BigDecimal defaultRate = new BigDecimal("150.00");
        return ResponseEntity.ok(Map.of(
                "rate", defaultRate,
                "updatedAt", Instant.now().toString(),
                "isDefault", true,
                "message", "デフォルトレートを使用中。レート更新ボタンで最新レートを取得してください。"));
    }

    /**
     * 為替レートを手動更新
     * 
     * 外部APIからレートを取得、または設定されていない場合はデフォルト値を設定
     */
    @PostMapping("/refresh")
    public ResponseEntity<?> refreshRate() {
        FxRateResult result = fxRateService.refreshRate();

        if (result.isSuccess()) {
            return ResponseEntity.ok(Map.of(
                    "rate", result.rate(),
                    "updatedAt", result.updatedAt() != null ? result.updatedAt().toString() : Instant.now().toString(),
                    "message", "レートを更新しました"));
        }

        // 更新失敗時もデフォルトレートで動作可能にする
        BigDecimal defaultRate = new BigDecimal("150.00");
        return ResponseEntity.ok(Map.of(
                "rate", defaultRate,
                "updatedAt", Instant.now().toString(),
                "isDefault", true,
                "message", "外部API取得失敗のためデフォルトレート(150円)を使用",
                "warn", result.error()));
    }
}
