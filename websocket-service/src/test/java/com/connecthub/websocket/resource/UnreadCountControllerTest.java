package com.connecthub.websocket.resource;

import com.connecthub.websocket.service.UnreadCountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * UnreadCountControllerTest — covers Fix #1 (ownership guard on GET + DELETE).
 *
 * KEY SECURITY INVARIANTS:
 *   - userId == requesterId  → 200/204 (owner access)
 *   - userId != requesterId, no role   → 403
 *   - userId != requesterId, PLATFORM_ADMIN → 200/204 (admin bypass)
 */
@ExtendWith(MockitoExtension.class)
class UnreadCountControllerTest {

    @Mock
    private UnreadCountService unreadCountService;

    private UnreadCountController controller;

    @BeforeEach
    void setUp() {
        controller = new UnreadCountController(unreadCountService);
    }

    // ── GET /{userId} ────────────────────────────────────────────────────────

    @Test
    void getUnreadCounts_ownerAccess_returns200() {
        when(unreadCountService.getAllForUser(42)).thenReturn(Map.of("room1", 3L));

        ResponseEntity<Map<String, Long>> response = controller.getUnreadCounts(42, 42, "");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(3L, response.getBody().get("room1"));
        verify(unreadCountService).getAllForUser(42);
    }

    @Test
    void getUnreadCounts_differentUser_returns403() {
        ResponseEntity<Map<String, Long>> response = controller.getUnreadCounts(42, 99, "USER");

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        verify(unreadCountService, never()).getAllForUser(anyInt());
    }

    @Test
    void getUnreadCounts_noRole_differentUser_returns403() {
        ResponseEntity<Map<String, Long>> response = controller.getUnreadCounts(42, 99, "");

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void getUnreadCounts_platformAdmin_canReadOtherUser() {
        when(unreadCountService.getAllForUser(42)).thenReturn(Map.of("room2", 5L));

        ResponseEntity<Map<String, Long>> response =
                controller.getUnreadCounts(42, 99, "PLATFORM_ADMIN");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(unreadCountService).getAllForUser(42);
    }

    @Test
    void getUnreadCounts_roleCheckCaseInsensitive_adminUppercase() {
        when(unreadCountService.getAllForUser(42)).thenReturn(Map.of());

        ResponseEntity<Map<String, Long>> response =
                controller.getUnreadCounts(42, 99, "platform_admin");

        // equalsIgnoreCase — both cases pass
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void getUnreadCounts_emptyUnread_returnsEmptyMap() {
        when(unreadCountService.getAllForUser(7)).thenReturn(Map.of());

        ResponseEntity<Map<String, Long>> response = controller.getUnreadCounts(7, 7, "");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().isEmpty());
    }

    // ── DELETE /{userId} ─────────────────────────────────────────────────────

    @Test
    void resetUnreadCount_ownerAccess_returns204() {
        ResponseEntity<Void> response = controller.resetUnreadCount(42, "room1", 42, "");

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(unreadCountService).reset(42, "room1");
    }

    @Test
    void resetUnreadCount_differentUser_returns403() {
        ResponseEntity<Void> response = controller.resetUnreadCount(42, "room1", 99, "USER");

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        verify(unreadCountService, never()).reset(anyInt(), anyString());
    }

    @Test
    void resetUnreadCount_noRole_differentUser_returns403() {
        ResponseEntity<Void> response = controller.resetUnreadCount(42, "room1", 99, "");

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        verify(unreadCountService, never()).reset(anyInt(), anyString());
    }

    @Test
    void resetUnreadCount_platformAdmin_canResetOtherUser() {
        ResponseEntity<Void> response =
                controller.resetUnreadCount(42, "room1", 99, "PLATFORM_ADMIN");

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(unreadCountService).reset(42, "room1");
    }
}
