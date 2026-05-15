package com.connecthub.websocket.resource;

import com.connecthub.websocket.service.UnreadCountService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/ws/unread")
@RequiredArgsConstructor
public class UnreadCountController {

    private final UnreadCountService unreadCountService;

    @GetMapping("/{userId}")
    public ResponseEntity<Map<String, Long>> getUnreadCounts(
            @PathVariable int userId,
            @RequestHeader("X-User-Id") int requesterId,
            @RequestHeader(value = "X-User-Role", defaultValue = "") String role) {

        if (userId != requesterId && !"PLATFORM_ADMIN".equalsIgnoreCase(role)) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(unreadCountService.getAllForUser(userId));
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> resetUnreadCount(
            @PathVariable int userId,
            @RequestParam String roomId,
            @RequestHeader("X-User-Id") int requesterId,
            @RequestHeader(value = "X-User-Role", defaultValue = "") String role) {

        if (userId != requesterId && !"PLATFORM_ADMIN".equalsIgnoreCase(role)) {
            return ResponseEntity.status(403).build();
        }
        unreadCountService.reset(userId, roomId);
        return ResponseEntity.noContent().build();
    }
}
