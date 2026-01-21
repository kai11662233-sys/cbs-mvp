package com.example.cbs_mvp.security;

import java.time.Instant;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;

@Service
public class JwtTokenService {

    @Value("${jwt.secret:default-secret-key-change-in-production-at-least-32-bytes}")
    private String jwtSecret;

    @Value("${jwt.expiration-hours:24}")
    private int expirationHours;

    private SecretKey key;

    @PostConstruct
    public void init() {
        // シークレットが短い場合はパディング
        String paddedSecret = jwtSecret;
        while (paddedSecret.length() < 32) {
            paddedSecret = paddedSecret + paddedSecret;
        }
        paddedSecret = paddedSecret.substring(0, 64); // 256-bit = 32 bytes = base64で約43文字
        this.key = Keys.hmacShaKeyFor(paddedSecret.getBytes());
    }

    public String generateToken(String username) {
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
}
