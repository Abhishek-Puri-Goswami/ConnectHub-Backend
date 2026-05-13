package com.connecthub.websocket.resource;

import com.connecthub.websocket.config.RedisConfig;
import com.connecthub.websocket.dto.BroadcastRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

/**
 * BroadcastController — Platform-wide admin message broadcast.
 *
 * POST /api/v1/ws/broadcast publishes a JSON event to the Redis "chat:broadcast" channel.
 * RedisMessageSubscriber on every websocket-service pod picks it up and forwards it to
 * the STOMP topic /topic/broadcast, which all connected clients subscribe to.
 *
 * Access is restricted to PLATFORM_ADMIN — the role check is enforced here rather than
 * relying solely on the API gateway so that internal callers are also blocked.
 */
@RestController
@RequestMapping("/api/v1/ws")
@RequiredArgsConstructor
@Slf4j
public class BroadcastController {

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    @PostMapping("/broadcast")
    public ResponseEntity<Void> broadcast(
            @RequestHeader(value = "X-User-Role", defaultValue = "") String role,
            @RequestHeader("X-User-Id") int actorId,
            @RequestBody BroadcastRequest req) {
        if (!"PLATFORM_ADMIN".equalsIgnoreCase(role))
            return ResponseEntity.status(403).build();
        if (req.getMessage() == null || req.getMessage().isBlank())
            return ResponseEntity.badRequest().build();
        try {
            String json = objectMapper.writeValueAsString(Map.of(
                    "type",    "BROADCAST",
                    "title",   req.getTitle() != null ? req.getTitle() : "",
                    "message", req.getMessage(),
                    "actorId", actorId,
                    "sentAt",  Instant.now().toString()
            ));
            redis.convertAndSend(RedisConfig.BROADCAST_CHANNEL, json);
            log.info("Platform broadcast sent by admin {}: \"{}\"", actorId, req.getTitle());
        } catch (Exception e) {
            log.error("Failed to publish broadcast: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
        return ResponseEntity.ok().build();
    }
}
