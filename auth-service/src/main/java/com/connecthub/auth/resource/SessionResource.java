package com.connecthub.auth.resource;

import com.connecthub.auth.config.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * SessionResource — Active Session Management Endpoints
 *
 * PURPOSE:
 *   Allows users to view and revoke their active sessions. Each JWT access token
 *   contains a unique jti (JWT ID) claim. When a session is revoked, the jti is
 *   added to a Redis blacklist. The gateway's JwtAuthenticationFilter checks this
 *   blacklist on every request and rejects blacklisted tokens.
 *
 * REDIS KEYS:
 *   "session:{userId}:{jti}" = JSON metadata about the session (device, login time).
 *   "token:blacklist:{jti}"  = marker key indicating a revoked token.
 *   "user:invalidated:{userId}" = marker key to invalidate ALL tokens for a user.
 *
 * SESSION TRACKING:
 *   Sessions are tracked when the user logs in (AuthServiceImpl.buildAuthResponse
 *   writes a session key with the jti). This resource reads those keys to list
 *   active sessions and writes blacklist keys to revoke them.
 */
@RestController
@RequestMapping("/api/v1/auth/sessions")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Sessions", description = "Active session management — list, revoke")
public class SessionResource {

    private final StringRedisTemplate redis;
    private final JwtUtil jwtUtil;

    /**
     * listSessions — returns all active sessions for the authenticated user.
     * Scans Redis for "session:{userId}:*" keys and returns the metadata.
     */
    @GetMapping
    @Operation(summary = "List active sessions")
    public ResponseEntity<List<Map<String, String>>> listSessions(
            @RequestHeader("X-User-Id") int userId) {
        String pattern = "session:" + userId + ":*";
        Set<String> keys = redis.keys(pattern);
        if (keys == null || keys.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }
        List<Map<String, String>> sessions = new ArrayList<>();
        for (String key : keys) {
            String jti = key.substring(key.lastIndexOf(':') + 1);
            String metadata = redis.opsForValue().get(key);
            Map<String, String> session = new HashMap<>();
            session.put("jti", jti);
            session.put("metadata", metadata != null ? metadata : "{}");
            Long ttl = redis.getExpire(key, TimeUnit.SECONDS);
            session.put("expiresInSeconds", ttl != null ? String.valueOf(ttl) : "unknown");
            sessions.add(session);
        }
        return ResponseEntity.ok(sessions);
    }

    /**
     * revokeSession — revokes a specific session by its jti.
     * Adds the jti to the token blacklist in Redis and removes the session key.
     * The blacklist TTL matches the access token expiry so it auto-cleans after
     * the token would have expired naturally anyway.
     */
    @DeleteMapping("/{jti}")
    @Operation(summary = "Revoke a specific session")
    public ResponseEntity<Void> revokeSession(
            @RequestHeader("X-User-Id") int userId,
            @PathVariable String jti) {
        // Remove the session tracking key
        redis.delete("session:" + userId + ":" + jti);
        // Add to token blacklist — gateway will reject requests with this jti
        long ttlSeconds = jwtUtil.getAccessExpiry() / 1000;
        redis.opsForValue().set("token:blacklist:" + jti, "revoked", ttlSeconds, TimeUnit.SECONDS);
        log.info("Session {} revoked for user {}", jti, userId);
        return ResponseEntity.noContent().build();
    }

    /**
     * revokeAllSessions — invalidates ALL active tokens for the user.
     * Sets a user-level invalidation key that the gateway checks. Any token
     * issued before this key was set will be rejected.
     */
    @DeleteMapping
    @Operation(summary = "Revoke all sessions (logout everywhere)")
    public ResponseEntity<Void> revokeAllSessions(
            @RequestHeader("X-User-Id") int userId) {
        // Delete all session tracking keys
        Set<String> keys = redis.keys("session:" + userId + ":*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
        // Set user-level invalidation key — all existing tokens are now rejected
        long ttlSeconds = jwtUtil.getAccessExpiry() / 1000;
        redis.opsForValue().set("user:invalidated:" + userId, String.valueOf(System.currentTimeMillis()),
                ttlSeconds, TimeUnit.SECONDS);
        log.info("All sessions revoked for user {}", userId);
        return ResponseEntity.noContent().build();
    }
}
