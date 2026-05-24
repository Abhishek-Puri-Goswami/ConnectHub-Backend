package com.connecthub.websocket.resource;

import com.connecthub.websocket.event.WebSocketEventListener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConnectionsControllerTest {

    @Mock private StringRedisTemplate redis;
    @Mock private ValueOperations<String, String> valueOps;

    @InjectMocks private ConnectionsController controller;

    @BeforeEach
    void setUp() {
        lenient().when(redis.opsForValue()).thenReturn(valueOps);
    }

    @Test
    void getConnectionCount_nonAdmin_returns403() {
        ResponseEntity<Long> resp = controller.getConnectionCount("USER");
        assertEquals(403, resp.getStatusCode().value());
        verifyNoInteractions(valueOps);
    }

    @Test
    void getConnectionCount_admin_returnsCountFromRedis() {
        when(valueOps.get(WebSocketEventListener.WS_CONNECTIONS_TOTAL)).thenReturn("42");

        ResponseEntity<Long> resp = controller.getConnectionCount("PLATFORM_ADMIN");

        assertEquals(200, resp.getStatusCode().value());
        assertEquals(42L, resp.getBody());
    }

    @Test
    void getConnectionCount_nullRedisValue_returnsZero() {
        when(valueOps.get(WebSocketEventListener.WS_CONNECTIONS_TOTAL)).thenReturn(null);

        ResponseEntity<Long> resp = controller.getConnectionCount("PLATFORM_ADMIN");

        assertEquals(200, resp.getStatusCode().value());
        assertEquals(0L, resp.getBody());
    }

    @Test
    void getConnectionCount_negativeRedisValue_returnsZero() {
        when(valueOps.get(WebSocketEventListener.WS_CONNECTIONS_TOTAL)).thenReturn("-3");

        ResponseEntity<Long> resp = controller.getConnectionCount("PLATFORM_ADMIN");

        assertEquals(200, resp.getStatusCode().value());
        assertEquals(0L, resp.getBody());
    }
}
