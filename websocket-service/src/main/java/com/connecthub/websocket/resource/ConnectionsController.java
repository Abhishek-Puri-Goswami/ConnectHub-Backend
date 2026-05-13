package com.connecthub.websocket.resource;

import com.connecthub.websocket.event.WebSocketEventListener;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ConnectionsController — exposes the live WebSocket session count to the admin dashboard.
 *
 * GET /api/v1/ws/connections/count returns the value of the Redis key
 * {@code ws:connections:total}, which is incremented on every STOMP connect and
 * decremented on every STOMP disconnect in {@link WebSocketEventListener}.
 *
 * This gives the real session count (not just unique users), so a user with
 * three browser tabs open contributes 3 to the counter.
 *
 * Access is restricted to PLATFORM_ADMIN — the role check is enforced here rather than
 * relying solely on the API gateway so that internal callers are also blocked.
 */
@RestController
@RequestMapping("/api/v1/ws")
@RequiredArgsConstructor
public class ConnectionsController {

    private final StringRedisTemplate redis;

    @GetMapping("/connections/count")
    public ResponseEntity<Long> getConnectionCount(
            @RequestHeader(value = "X-User-Role", defaultValue = "") String role) {

        if (!"PLATFORM_ADMIN".equalsIgnoreCase(role)) {
            return ResponseEntity.status(403).build();
        }

        String raw = redis.opsForValue().get(WebSocketEventListener.WS_CONNECTIONS_TOTAL);
        long count = raw != null ? Long.parseLong(raw) : 0L;
        return ResponseEntity.ok(Math.max(0, count));
    }
}
