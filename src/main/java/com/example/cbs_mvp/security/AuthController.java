package com.example.cbs_mvp.security;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.cbs_mvp.ops.OpsKeyService;
import com.example.cbs_mvp.ops.SystemFlagService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final JwtTokenService jwtTokenService;
    private final SystemFlagService flagService;
    private final PasswordEncoder passwordEncoder;
    private final OpsKeyService opsKeyService;

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
     * 
     * 認証が必要:
     * - JWTトークン (Authorization: Bearer xxx)
     * - または X-OPS-KEY ヘッダー
     * - または 現在のパスワードが正しい（自分自身の変更）
     */
    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(
            @RequestHeader(value = "X-OPS-KEY", required = false) String opsKey,
            @RequestBody ChangePasswordRequest request) {
        if (request == null || request.username == null
                || request.currentPassword == null || request.newPassword == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "username, currentPassword, and newPassword are required"));
        }

        // 新しいパスワードの長さチェック
        if (request.newPassword.length() < 8) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "newPassword must be at least 8 characters"));
        }

        String username = request.username.trim().toLowerCase();
        String flagKey = "USER_" + username.toUpperCase() + "_HASH";
        String storedHash = flagService.get(flagKey);

        // 認証チェック: OPS-KEY または JWT または 現在のパスワード
        boolean isOpsKeyValid = opsKeyService.isValid(opsKey);
        boolean isJwtAuthenticated = isAuthenticated();
        boolean isCurrentPasswordValid = false;

        // 既存ユーザーの場合は現在のパスワード確認
        if (storedHash != null && !storedHash.isBlank()) {
            isCurrentPasswordValid = passwordEncoder.matches(request.currentPassword, storedHash);
        } else {
            // 初回admin設定の場合はデフォルトパスワード確認
            if ("admin".equals(username) && "admin".equals(request.currentPassword)) {
                isCurrentPasswordValid = true;
            }
        }

        // 3つのうちいずれかの認証が必要
        if (!isOpsKeyValid && !isJwtAuthenticated && !isCurrentPasswordValid) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "authentication required (OPS-KEY, JWT, or valid currentPassword)"));
        }

        // JWT認証の場合は、自分自身またはadminのみ変更可能
        if (isJwtAuthenticated && !isOpsKeyValid && !isCurrentPasswordValid) {
            String authenticatedUser = getAuthenticatedUsername();
            if (!username.equals(authenticatedUser) && !"admin".equals(authenticatedUser)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "can only change your own password"));
            }
        }

        // 新規ユーザー作成は OPS-KEY または admin JWT が必要
        if (storedHash == null || storedHash.isBlank()) {
            if (!isOpsKeyValid && !"admin".equals(username)) {
                boolean isAdminJwt = isJwtAuthenticated && "admin".equals(getAuthenticatedUsername());
                if (!isAdminJwt) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body(Map.of("error", "only admin or OPS-KEY can create new users"));
                }
            }
        }

        // 新しいパスワードをハッシュして保存
        String newHash = passwordEncoder.encode(request.newPassword);
        flagService.set(flagKey, newHash);

        return ResponseEntity.ok(Map.of(
                "message", "password changed successfully",
                "username", username));
    }

    private boolean isAuthenticated() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal());
    }

    private String getAuthenticatedUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            return auth.getName();
        }
        return null;
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
