package com.connecthub.auth.resource;

import com.connecthub.auth.client.MessageClient;
import com.connecthub.auth.client.PresenceClient;
import com.connecthub.auth.client.RoomClient;
import com.connecthub.auth.repository.UserRepository;
import com.connecthub.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * PublicStatsResource — Unauthenticated platform statistics endpoint.
 *
 * Exposes aggregated, read-only metrics for the public landing page.
 * No JWT is required. The API Gateway's OPEN_ENDPOINTS list includes
 * /api/v1/auth/public/ so this passes through without a token.
 *
 * Each Feign call is individually try-caught so a single service being
 * down does not break the entire stats response — it falls back to 0.
 */
@RestController
@RequestMapping("/api/v1/auth/public")
@RequiredArgsConstructor
@Tag(name = "Public", description = "Unauthenticated public endpoints")
public class PublicStatsResource {

    private final AuthService authService;
    private final UserRepository userRepository;
    private final PresenceClient presenceClient;
    private final RoomClient roomClient;
    private final MessageClient messageClient;

    /**
     * GET /api/v1/auth/public/verification-status?email=...
     * Returns whether the email and phone are verified for a given email address.
     * Used by the registration verification page to pre-populate verified state
     * (e.g. if the user navigates back after already verifying one or both).
     * No auth required — the gateway's OPEN_ENDPOINTS list includes /api/v1/auth/public/.
     */
    @Operation(summary = "Check email/phone verification status (no auth required)")
    @GetMapping("/verification-status")
    public ResponseEntity<Map<String, Boolean>> getVerificationStatus(@RequestParam String email) {
        return userRepository.findByEmail(email)
            .map(u -> ResponseEntity.ok(Map.of(
                "emailVerified", u.isEmailVerified(),
                "phoneVerified", u.isPhoneVerified()
            )))
            .orElseGet(() -> ResponseEntity.ok(Map.of(
                "emailVerified", false,
                "phoneVerified", false
            )));
    }

    @Operation(summary = "Platform statistics (no auth required)")
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getPublicStats() {
        Map<String, Object> stats = new LinkedHashMap<>();

        // Total registered users — auth-service has direct DB access, no Feign needed
        try {
            stats.put("totalUsers", authService.getAllUsers().size());
        } catch (Exception e) {
            stats.put("totalUsers", 0);
        }

        // Currently online users (from presence-service via Feign)
        try {
            stats.put("onlineUsers", presenceClient.getOnlineCount());
        } catch (Exception e) {
            stats.put("onlineUsers", 0);
        }

        // Active rooms in the last 24 h (from room-service via Feign)
        try {
            stats.put("activeRooms", roomClient.countActiveRooms());
        } catch (Exception e) {
            stats.put("activeRooms", 0);
        }

        // Messages sent since midnight today (from message-service via Feign)
        try {
            stats.put("messagesToday", messageClient.countToday());
        } catch (Exception e) {
            stats.put("messagesToday", 0);
        }

        return ResponseEntity.ok(stats);
    }
}
