package com.example.cbs_mvp.security;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.cbs_mvp.ops.SystemFlagService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final JwtTokenService jwtTokenService;
    private final SystemFlagService flagService;
    private final PasswordEncoder passwordEncoder;

    /**
     * ログイン: ユーザー名/パスワードでJWTトークン発行
     * 
     * ユーザー管理は簡易的にSystemFlagで実装。
     * USER_<username>_HASH に BCryptハッシュを保存。
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        if (request == null || request.username == null || request.password == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "username and password are required"));
        }

        String username = request.username.trim().toLowerCase();
        if (username.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "username is required"));
        }

        // SystemFlagからパスワードハッシュを取得
        String storedHash = flagService.get("USER_" + username.toUpperCase() + "_HASH");

        if (storedHash == null || storedHash.isBlank()) {
            // デフォルト管理者: admin / admin (本番では必ず変更すること)
            if ("admin".equals(username)) {
                String defaultHash = flagService.get("USER_ADMIN_HASH");
                if (defaultHash == null || defaultHash.isBlank()) {
                    // 初回起動時はデフォルトパスワードを許可
                    if ("admin".equals(request.password)) {
                        String token = jwtTokenService.generateToken(username);
                        return ResponseEntity.ok(Map.of(
                                "token", token,
                                "username", username,
                                "warning", "default admin password - please change immediately"));
                    }
                }
            }
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "invalid credentials"));
        }

        if (!passwordEncoder.matches(request.password, storedHash)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "invalid credentials"));
        }

        String token = jwtTokenService.generateToken(username);
        return ResponseEntity.ok(Map.of(
                "token", token,
                "username", username));
    }

    /**
     * パスワード変更
     */
    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(@RequestBody ChangePasswordRequest request) {
        if (request == null || request.username == null
                || request.currentPassword == null || request.newPassword == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "username, currentPassword, and newPassword are required"));
        }

        String username = request.username.trim().toLowerCase();
        String flagKey = "USER_" + username.toUpperCase() + "_HASH";
        String storedHash = flagService.get(flagKey);

        // 既存ユーザーの場合は現在のパスワード確認
        if (storedHash != null && !storedHash.isBlank()) {
            if (!passwordEncoder.matches(request.currentPassword, storedHash)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "current password is incorrect"));
            }
        } else {
            // 新規ユーザー作成（admin権限が必要 - 簡易実装）
            if (!"admin".equals(username)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "user does not exist"));
            }
        }

        // 新しいパスワードをハッシュして保存
        String newHash = passwordEncoder.encode(request.newPassword);
        flagService.set(flagKey, newHash);

        return ResponseEntity.ok(Map.of(
                "message", "password changed successfully",
                "username", username));
    }

    public static class LoginRequest {
        public String username;
        public String password;
    }

    public static class ChangePasswordRequest {
        public String username;
        public String currentPassword;
        public String newPassword;
    }
}
