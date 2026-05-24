package com.connecthub.auth.resource;

import com.connecthub.auth.client.MessageClient;
import com.connecthub.auth.client.PresenceClient;
import com.connecthub.auth.client.RoomClient;
import com.connecthub.auth.entity.User;
import com.connecthub.auth.repository.UserRepository;
import com.connecthub.auth.service.AuthService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PublicStatsResourceTest {

    @Mock private AuthService authService;
    @Mock private UserRepository userRepository;
    @Mock private PresenceClient presenceClient;
    @Mock private RoomClient roomClient;
    @Mock private MessageClient messageClient;

    @InjectMocks private PublicStatsResource resource;

    // ── getVerificationStatus ─────────────────────────────────────────────────

    @Test
    void getVerificationStatus_userExists_returnsVerifiedFlags() {
        User u = new User();
        u.setEmailVerified(true);
        u.setPhoneVerified(false);
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(u));

        ResponseEntity<Map<String, Boolean>> resp = resource.getVerificationStatus("user@test.com");

        assertEquals(200, resp.getStatusCode().value());
        assertNotNull(resp.getBody());
        assertTrue(resp.getBody().get("emailVerified"));
        assertFalse(resp.getBody().get("phoneVerified"));
    }

    @Test
    void getVerificationStatus_userNotFound_returnsBothFalse() {
        when(userRepository.findByEmail("nobody@test.com")).thenReturn(Optional.empty());

        ResponseEntity<Map<String, Boolean>> resp = resource.getVerificationStatus("nobody@test.com");

        assertEquals(200, resp.getStatusCode().value());
        assertNotNull(resp.getBody());
        assertFalse(resp.getBody().get("emailVerified"));
        assertFalse(resp.getBody().get("phoneVerified"));
    }

    // ── getPublicStats ────────────────────────────────────────────────────────

    @Test
    void getPublicStats_allClientsSucceed_returnsFullStats() {
        when(authService.getAllUsers()).thenReturn(List.of(new User(), new User()));
        when(presenceClient.getOnlineCount()).thenReturn(5);
        when(roomClient.countActiveRooms()).thenReturn(10L);
        when(messageClient.countToday()).thenReturn(100L);

        ResponseEntity<Map<String, Object>> resp = resource.getPublicStats();

        assertEquals(200, resp.getStatusCode().value());
        assertNotNull(resp.getBody());
        assertEquals(2, resp.getBody().get("totalUsers"));
        assertEquals(5, resp.getBody().get("onlineUsers"));
        assertEquals(10L, resp.getBody().get("activeRooms"));
        assertEquals(100L, resp.getBody().get("messagesToday"));
    }

    @Test
    void getPublicStats_presenceClientFails_defaultsOnlineToZero() {
        when(authService.getAllUsers()).thenReturn(List.of());
        when(presenceClient.getOnlineCount()).thenThrow(new RuntimeException("timeout"));
        when(roomClient.countActiveRooms()).thenReturn(0L);
        when(messageClient.countToday()).thenReturn(0L);

        ResponseEntity<Map<String, Object>> resp = resource.getPublicStats();

        assertEquals(200, resp.getStatusCode().value());
        assertEquals(0, resp.getBody().get("onlineUsers"));
    }

    @Test
    void getPublicStats_roomClientFails_defaultsActiveRoomsToZero() {
        when(authService.getAllUsers()).thenReturn(List.of());
        when(presenceClient.getOnlineCount()).thenReturn(0);
        when(roomClient.countActiveRooms()).thenThrow(new RuntimeException("unavailable"));
        when(messageClient.countToday()).thenReturn(0L);

        ResponseEntity<Map<String, Object>> resp = resource.getPublicStats();

        assertEquals(200, resp.getStatusCode().value());
        assertEquals(0, resp.getBody().get("activeRooms"));
    }

    @Test
    void getPublicStats_messageClientFails_defaultsMessagesTodayToZero() {
        when(authService.getAllUsers()).thenReturn(List.of());
        when(presenceClient.getOnlineCount()).thenReturn(0);
        when(roomClient.countActiveRooms()).thenReturn(0L);
        when(messageClient.countToday()).thenThrow(new RuntimeException("down"));

        ResponseEntity<Map<String, Object>> resp = resource.getPublicStats();

        assertEquals(200, resp.getStatusCode().value());
        assertEquals(0, resp.getBody().get("messagesToday"));
    }

    @Test
    void getPublicStats_authServiceFails_defaultsTotalUsersToZero() {
        when(authService.getAllUsers()).thenThrow(new RuntimeException("db error"));
        when(presenceClient.getOnlineCount()).thenReturn(0);
        when(roomClient.countActiveRooms()).thenReturn(0L);
        when(messageClient.countToday()).thenReturn(0L);

        ResponseEntity<Map<String, Object>> resp = resource.getPublicStats();

        assertEquals(200, resp.getStatusCode().value());
        assertEquals(0, resp.getBody().get("totalUsers"));
    }
}
