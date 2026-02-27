package com.logbook.logbookbackend.config;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
@Slf4j
public class JwtUtil {

    private final SecretKey key;
    private final long expiryMs;

    // State tokens for GitHub OAuth are short-lived (10 minutes)
    private static final long STATE_TOKEN_EXPIRY_MS = 10 * 60 * 1000L;

    public JwtUtil(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiry-days}") int expiryDays
    ) {
        this.key      = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiryMs = (long) expiryDays * 24 * 60 * 60 * 1000;
    }

    public String generateToken(Long userId, String email) {
        Date now    = new Date();
        Date expiry = new Date(now.getTime() + expiryMs);

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("email", email)
                .claim("type", "access")
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    /**
     * Generate a short-lived signed state token carrying the userId.
     * Used so the GitHub OAuth callback (a browser redirect with no Authorization header)
     * can securely recover which user initiated the OAuth flow.
     */
    public String generateStateToken(Long userId) {
        Date now    = new Date();
        Date expiry = new Date(now.getTime() + STATE_TOKEN_EXPIRY_MS);

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("type", "github_state")
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    public boolean isValidStateToken(String token) {
        try {
            Claims claims = parseClaims(token);
            return "github_state".equals(claims.get("type", String.class));
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Invalid state token: {}", e.getMessage());
            return false;
        }
    }

    public Long extractUserIdFromState(String token) {
        String subject = parseClaims(token).getSubject();
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("State token has no subject");
        }
        return Long.parseLong(subject);
    }

    public Long extractUserId(String token) {
        return Long.parseLong(parseClaims(token).getSubject());
    }

    public String extractEmail(String token) {
        return (String) parseClaims(token).get("email");
    }

    public boolean isValid(String token) {
        try {
            Claims claims = parseClaims(token);
            // Only accept access tokens (not state tokens) for API auth
            String type = claims.get("type", String.class);
            return type == null || "access".equals(type);
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Invalid JWT: {}", e.getMessage());
            return false;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}