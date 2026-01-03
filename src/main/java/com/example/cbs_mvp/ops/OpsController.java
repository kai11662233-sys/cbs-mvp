package com.example.cbs_mvp.ops;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/ops")
public class OpsController {

    private final OpsKeyService opsKeyService;
    private final KillSwitchService killSwitchService;

    public OpsController(OpsKeyService opsKeyService, KillSwitchService killSwitchService) {
        this.opsKeyService = opsKeyService;
        this.killSwitchService = killSwitchService;
        // dev用：OPS_KEYが無いなら初期値を入れる（本番は外す）
        this.opsKeyService.ensureDefaultOpsKeyIfMissing("dev-ops-key");
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        boolean paused = killSwitchService.isPaused();
        String reason = killSwitchService.getReason();
        LocalDateTime updatedAt = killSwitchService.getUpdatedAt();
        return Map.of(
                "paused", paused,
                "reason", reason,
                "updatedAt", updatedAt
        );
    }

    @PostMapping("/pause")
    public ResponseEntity<?> pause(
            @RequestHeader(value = "X-OPS-KEY", required = false) String opsKey,
            @RequestBody(required = false) Map<String, Object> body
    ) {
        if (!opsKeyService.isValid(opsKey)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "invalid ops key"));
        }
        String reason = body == null ? "" : String.valueOf(body.getOrDefault("reason", ""));
        killSwitchService.pause(reason);
        return ResponseEntity.ok(Map.of("paused", true, "reason", reason));
    }

    @PostMapping("/resume")
    public ResponseEntity<?> resume(@RequestHeader(value = "X-OPS-KEY", required = false) String opsKey) {
        if (!opsKeyService.isValid(opsKey)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "invalid ops key"));
        }
        killSwitchService.resume();
        return ResponseEntity.ok(Map.of("paused", false));
    }
}
