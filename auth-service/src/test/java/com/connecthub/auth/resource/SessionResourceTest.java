package com.connecthub.auth.resource;

import com.connecthub.auth.config.JwtUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SessionResourceTest {

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks
    private SessionResource sessionResource;

    @Test
    @SuppressWarnings("unchecked")
    void listSessions_empty() {
        when(redis.keys(anyString())).thenReturn(null);
        ResponseEntity<List<Map<String, String>>> res = sessionResource.listSessions(1);
        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertTrue(res.getBody().isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void listSessions_withData() {
        when(redis.keys("session:1:*")).thenReturn(Set.of("session:1:abc"));
        ValueOperations<String, String> valOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valOps);
        when(valOps.get("session:1:abc")).thenReturn("{\"device\":\"pc\"}");
        
        ResponseEntity<List<Map<String, String>>> res = sessionResource.listSessions(1);
        
        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertEquals(1, res.getBody().size());
        assertEquals("abc", res.getBody().get(0).get("jti"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void revokeSession() {
        ValueOperations<String, String> valOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valOps);
        when(jwtUtil.getAccessExpiry()).thenReturn(3600000L);

        ResponseEntity<Void> res = sessionResource.revokeSession(1, "abc");

        assertEquals(HttpStatus.NO_CONTENT, res.getStatusCode());
        verify(redis).delete("session:1:abc");
        verify(valOps).set(eq("token:blacklist:abc"), eq("revoked"), eq(3600L), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void revokeAllSessions() {
        when(redis.keys("session:1:*")).thenReturn(Set.of("key1"));
        ValueOperations<String, String> valOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valOps);
        when(jwtUtil.getAccessExpiry()).thenReturn(3600000L);

        ResponseEntity<Void> res = sessionResource.revokeAllSessions(1);

        assertEquals(HttpStatus.NO_CONTENT, res.getStatusCode());
        verify(redis).delete(any(Set.class));
        verify(valOps).set(eq("user:invalidated:1"), anyString(), eq(3600L), any());
    }
}
