package com.connecthub.websocket.resource;

import com.connecthub.websocket.service.UnreadCountService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/ws/unread")
@RequiredArgsConstructor
public class UnreadCountController {

    private final UnreadCountService unreadCountService;

    @GetMapping("/{userId}")
    public ResponseEntity<Map<String, Long>> getUnreadCounts(@PathVariable int userId) {
        return ResponseEntity.ok(unreadCountService.getAllForUser(userId));
    }
}
