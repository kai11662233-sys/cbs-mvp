package com.example.cbs_mvp.security;

import java.time.Instant;
import java.util.Date;

import javax.crypto.SecretKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;

@Service
public class JwtTokenService {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenService.class);
    private static final int MINIMUM_SECRET_LENGTH = 32;
    private static final String DEFAULT_SECRET_PREFIX = "default-secret";

    @Value("${jwt.secret:default-secret-key-change-in-production-at-least-32-bytes}")
    private String jwtSecret;

    @Value("${jwt.expiration-hours:24}")
    private int expirationHours;

    private SecretKey key;
    private boolean usingDefaultSecret = false;

    @PostConstruct
    public void init() {
        // デフォルトシークレット使用の警告
        if (jwtSecret == null || jwtSecret.isBlank() || jwtSecret.startsWith(DEFAULT_SECRET_PREFIX)) {
            usingDefaultSecret = true;
            log.warn("======================================");
            log.warn("⚠️ WARNING: Using default JWT secret!");
            log.warn("This is INSECURE for production use.");
            log.warn("Set JWT_SECRET environment variable.");
            log.warn("======================================");
        }

        // 最低長チェック
        if (jwtSecret.length() < MINIMUM_SECRET_LENGTH) {
            log.error("JWT secret is too short! Minimum {} characters required, got {}.",
                    MINIMUM_SECRET_LENGTH, jwtSecret.length());
            throw new IllegalStateException(
                    "JWT secret must be at least " + MINIMUM_SECRET_LENGTH + " characters. " +
                            "Set JWT_SECRET environment variable with a secure value.");
        }

        // 安全な鍵生成（UTF-8明示で環境依存を排除）
        byte[] keyBytes = jwtSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            // この時点でエラーになるはずだが、念のため
            throw new IllegalStateException("JWT secret must be at least 32 bytes");
        }

        // 32バイト以上あれば直接使用
        this.key = Keys.hmacShaKeyFor(keyBytes);
        log.info("JWT service initialized. Token expiration: {} hours", expirationHours);
    }

    public String generateToken(String username) {
        if (usingDefaultSecret) {
            log.warn("Generating JWT with default secret - NOT SECURE for production!");
        }

        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(expirationHours * 3600L);

        return Jwts.builder()
                .subject(username)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(key)
                .compact();
    }

    public String validateTokenAndGetUsername(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            if (claims.getExpiration().before(new Date())) {
                return null;
            }

            return claims.getSubject();
        } catch (Exception e) {
            return null;
        }
    }

    public boolean isTokenValid(String token) {
        return validateTokenAndGetUsername(token) != null;
    }

    public boolean isUsingDefaultSecret() {
        return usingDefaultSecret;
    }
}
