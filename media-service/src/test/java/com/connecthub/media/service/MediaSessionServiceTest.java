package com.connecthub.media.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MediaSessionServiceTest {

    private StringRedisTemplate redis;
    private ValueOperations<String, String> ops;
    private MediaSessionService svc;
    private static final long NOW = 1_800_000_000L;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        svc = new MediaSessionService("test-secret-test-secret-test-secret", redis);
    }

    @Test
    void issuedToken_verifiesToTheSameUser() {
        assertEquals(Optional.of(42), svc.verify(svc.issue(42, NOW), NOW + 10));
    }

    @Test
    void expiredToken_isRejected() {
        String t = svc.issue(42, NOW);
        assertTrue(svc.verify(t, NOW + MediaSessionService.LIFETIME_SECONDS - 1).isPresent());
        assertTrue(svc.verify(t, NOW + MediaSessionService.LIFETIME_SECONDS).isEmpty());
    }

    @Test
    void tamperedUserIdOrExpiry_isRejected() {
        String[] p = svc.issue(42, NOW).split("[.]");
        assertTrue(svc.verify(String.join(".", p[0], "1", p[2], p[3], p[4]), NOW).isEmpty());          // other user
        assertTrue(svc.verify(String.join(".", p[0], p[1], p[2], "9999999999", p[4]), NOW).isEmpty()); // longer life
    }

    @Test
    void tokenSignedWithAnotherSecret_isRejected() {
        MediaSessionService other = new MediaSessionService("a-completely-different-secret-value", redis);
        assertTrue(svc.verify(other.issue(42, NOW), NOW).isEmpty());
    }

    @Test
    void garbage_isRejected() {
        for (String t : new String[]{null, "", "abc", "v1.1.2.3", "v2.1.2.3.x", "a.b.c.d.e", "eyJhbGciOiJIUzI1NiJ9.e30.x"})
            assertTrue(svc.verify(t, NOW).isEmpty(), String.valueOf(t));
    }

    @Test
    void suspendedUser_isRejected() {
        when(redis.hasKey("user:suspended:42")).thenReturn(true);
        assertTrue(svc.verify(svc.issue(42, NOW), NOW).isEmpty());
    }

    @Test
    void sessionIssuedBeforeInvalidation_isRejected_laterOneIsFine() {
        when(ops.get("user:invalidated:42")).thenReturn(String.valueOf((NOW + 100) * 1000)); // e.g. password reset
        assertTrue(svc.verify(svc.issue(42, NOW), NOW + 200).isEmpty());
        assertTrue(svc.verify(svc.issue(42, NOW + 200), NOW + 210).isPresent());
    }
}
