package com.connecthub.websocket.resource;

import com.connecthub.websocket.config.RedisConfig;
import com.connecthub.websocket.dto.BroadcastRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BroadcastControllerTest {

    @Mock private StringRedisTemplate redis;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks private BroadcastController controller;

    private BroadcastRequest req(String title, String message) {
        BroadcastRequest r = new BroadcastRequest();
        r.setTitle(title);
        r.setMessage(message);
        return r;
    }

    // ── Role check ────────────────────────────────────────────────────────────

    @Test
    void broadcast_nonAdmin_returns403() {
        ResponseEntity<Void> resp = controller.broadcast("USER", 1, req("t", "msg"));
        assertEquals(403, resp.getStatusCode().value());
        verifyNoInteractions(redis);
    }

    @Test
    void broadcast_emptyRole_returns403() {
        ResponseEntity<Void> resp = controller.broadcast("", 1, req("t", "msg"));
        assertEquals(403, resp.getStatusCode().value());
        verifyNoInteractions(redis);
    }

    // ── Message validation ────────────────────────────────────────────────────

    @Test
    void broadcast_nullMessage_returns400() {
        ResponseEntity<Void> resp = controller.broadcast("PLATFORM_ADMIN", 1, req("t", null));
        assertEquals(400, resp.getStatusCode().value());
        verifyNoInteractions(redis);
    }

    @Test
    void broadcast_blankMessage_returns400() {
        ResponseEntity<Void> resp = controller.broadcast("PLATFORM_ADMIN", 1, req("t", "   "));
        assertEquals(400, resp.getStatusCode().value());
        verifyNoInteractions(redis);
    }

    // ── Happy path ────────────────────────────────────────────────────────────

    @Test
    void broadcast_validRequest_publishesToRedisAndReturns200() throws Exception {
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"type\":\"BROADCAST\"}");

        ResponseEntity<Void> resp = controller.broadcast("PLATFORM_ADMIN", 42, req("Alert", "Hello!"));

        assertEquals(200, resp.getStatusCode().value());
        verify(redis).convertAndSend(eq(RedisConfig.BROADCAST_CHANNEL), anyString());
    }

    @Test
    void broadcast_caseInsensitiveRole_adminAllowed() throws Exception {
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        ResponseEntity<Void> resp = controller.broadcast("platform_admin", 1, req(null, "Test message"));

        assertEquals(200, resp.getStatusCode().value());
        verify(redis).convertAndSend(anyString(), anyString());
    }

    @Test
    void broadcast_nullTitle_usesEmptyStringInPayload() throws Exception {
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        ResponseEntity<Void> resp = controller.broadcast("PLATFORM_ADMIN", 1, req(null, "No title"));

        assertEquals(200, resp.getStatusCode().value());
        verify(redis).convertAndSend(eq(RedisConfig.BROADCAST_CHANNEL), anyString());
    }

    // ── Serialization failure ─────────────────────────────────────────────────

    @Test
    void broadcast_serializationFails_returns500() throws Exception {
        when(objectMapper.writeValueAsString(any())).thenThrow(new com.fasterxml.jackson.core.JsonProcessingException("err") {});

        ResponseEntity<Void> resp = controller.broadcast("PLATFORM_ADMIN", 1, req("t", "msg"));

        assertEquals(500, resp.getStatusCode().value());
        verify(redis, never()).convertAndSend(anyString(), anyString());
    }
}
