package com.connecthub.auth.config;

import com.connecthub.auth.entity.User;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JwtUtil — creates, signs, and reads JWT tokens for the auth-service.
 *
 * Only the auth-service ever issues tokens. All other services receive tokens
 * from the frontend and the API Gateway validates them locally using the same
 * shared secret — so no inter-service call is needed for token verification.
 *
 * Three token types:
 *
 *   Access token  — short-lived (default 24 h). Carries the user's identity:
 *                   userId, email, username, role, subscriptionTier. The
 *                   gateway reads these and forwards them as X-User-* headers.
 *
 *   Refresh token — long-lived (default 7 days). Contains only userId and
 *                   type="refresh". The auth-service /refresh endpoint accepts
 *                   this and issues a fresh access token from the current DB
 *                   state, so role/tier changes are reflected on next refresh.
 *
 *   Reset token   — very short-lived (15 min). Contains purpose="PASSWORD_RESET"
 *                   to prevent misuse as a regular access token. Avoids the need
 *                   to store any server-side reset session state.
 *
 * Signing key:
 *   HMAC-SHA256, derived from a Base64-encoded secret in application.yml
 *   (jwt.secret). The API Gateway uses the same secret so it can verify tokens
 *   without calling auth-service.
 *
 * Claims stored in the access token:
 *   sub (subject)    — userId as a string, e.g. "42"
 *   email            — user's email address
 *   username         — user's username
 *   role             — USER | ADMIN | PLATFORM_ADMIN
 *   subscriptionTier — FREE | PREMIUM | PLATINUM
 *   jti              — unique token ID (used to track sessions and blacklist tokens)
 *   iat / exp        — issued-at and expiry as Unix timestamps
 */
// Spring bean — allows this utility to be injected into any service or filter
@Component
public class JwtUtil {

    // Reads jwt.secret from application.yml — the Base64-encoded signing key
    @Value("${jwt.secret}")
    private String jwtSecret;

    // Access token lifetime in ms — default 86400000 = 24 hours
    @Value("${jwt.access-token-expiry:86400000}")
    private long accessExpiry;

    // Refresh token lifetime in ms — default 604800000 = 7 days
    @Value("${jwt.refresh-token-expiry:604800000}")
    private long refreshExpiry;

    /**
     * Builds an HMAC-SHA signing key from the Base64-encoded secret.
     * Called on every token operation — the key object is cheap to create each time.
     */
    private SecretKey key() {
        return Keys.hmacShaKeyFor(Base64.getDecoder().decode(jwtSecret));
    }

    /**
     * Creates a signed JWT with the user's full identity claims.
     *
     * Role-based tier override: PLATFORM_ADMIN always gets PLATINUM, ADMIN always gets
     * PREMIUM regardless of what is stored in the database. This ensures admin accounts
     * always have their correct plan tier reflected in the token without manual DB changes.
     *
     * A random jti (JWT ID) is included so each token can be individually tracked and
     * blacklisted on logout.
     */
    public String generateAccessToken(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("email", user.getEmail());
        claims.put("username", user.getUsername());
        claims.put("role", user.getRole());
        String tier = user.getSubscriptionTier() != null ? user.getSubscriptionTier() : "FREE";
        String role = user.getRole() != null ? user.getRole().toUpperCase() : "USER";
        if ("PLATFORM_ADMIN".equals(role)) {
            tier = "PLATINUM";
        } else if ("ADMIN".equals(role) && !"PLATINUM".equals(tier)) {
            tier = "PREMIUM";
        }
        claims.put("subscriptionTier", tier);
        claims.put("jti", java.util.UUID.randomUUID().toString());
        return Jwts.builder()
                .subject(String.valueOf(user.getUserId()))
                .claims(claims)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + accessExpiry))
                .signWith(key())
                .compact();
    }

    /**
     * Creates a minimal long-lived token used to get a new access token when it expires.
     * Intentionally holds only the userId — no profile data. This means a fresh access
     * token generated from this refresh token will always reflect the latest role and tier
     * from the database.
     */
    public String generateRefreshToken(User user) {
        return Jwts.builder()
                .subject(String.valueOf(user.getUserId()))
                .claim("type", "refresh")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + refreshExpiry))
                .signWith(key())
                .compact();
    }

    /**
     * Creates a 15-minute token that authorizes a password reset.
     * The "purpose" claim guards against using this token as a regular access token —
     * the resetPassword endpoint verifies it before accepting the new password.
     */
    public String generateResetToken(int userId) {
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("purpose", "PASSWORD_RESET")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 900000))
                .signWith(key())
                .compact();
    }

    /**
     * Parses and verifies a JWT string, returning its claims map.
     * Throws JwtException (and subclasses) if the token is expired, malformed,
     * or signed with a different key. Callers must catch JwtException.
     */
    public Claims parseToken(String token) {
        return Jwts.parser().verifyWith(key()).build().parseSignedClaims(token).getPayload();
    }

    /**
     * Returns true if the token passes signature and expiry checks.
     * Lets callers test a token without having to catch exceptions themselves.
     */
    public boolean isValid(String token) {
        try {
            parseToken(token);
            return true;
        } catch (JwtException e) {
            return false;
        }
    }

    /** Extracts the numeric userId from the token's "sub" (subject) claim. */
    public int getUserId(String token) {
        return Integer.parseInt(parseToken(token).getSubject());
    }

    /** Returns the configured access token lifetime in milliseconds. */
    public long getAccessExpiry() {
        return accessExpiry;
    }

    /**
     * Pulls a specific value from the token's claims using a resolver function.
     * Example: extractClaim(token, c -> c.get("jti", String.class))
     */
    public <T> T extractClaim(String token, java.util.function.Function<Claims, T> resolver) {
        Claims claims = parseToken(token);
        return resolver.apply(claims);
    }
}
