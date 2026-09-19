package com.connecthub.auth.service;

import com.connecthub.auth.exception.TooManyRequestsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Backed by a tiny in-memory fake of the Redis calls used, so counting and locking are really exercised. */
class LoginAttemptServiceTest {

    private final Map<String, Long> store = new HashMap<>();
    private final Map<String, Long> ttl = new HashMap<>();
    private StringRedisTemplate redis;
    private LoginAttemptService svc;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.increment(anyString())).thenAnswer(inv -> store.merge(inv.getArgument(0), 1L, Long::sum));
        when(ops.get(anyString())).thenAnswer(inv -> {
            Long v = store.get(inv.getArgument(0));
            return v == null ? null : String.valueOf(v);
        });
        when(redis.expire(anyString(), anyLong(), any(TimeUnit.class))).thenAnswer(inv -> {
            ttl.put(inv.getArgument(0), inv.getArgument(1));
            return true;
        });
        when(redis.getExpire(anyString(), any(TimeUnit.class)))
                .thenAnswer(inv -> ttl.getOrDefault((String) inv.getArgument(0), -1L));
        when(redis.delete(anyString())).thenAnswer(inv -> store.remove((String) inv.getArgument(0)) != null);
        svc = new LoginAttemptService(redis);
    }

    private void fail(String key, int times) {
        for (int i = 0; i < times; i++) svc.recordAccountFailure(key);
    }

    @Test
    void fourFailuresStillAllowed_fifthLocksTheAccount() {
        String k = LoginAttemptService.userAccountKey(7);
        fail(k, 4);
        assertDoesNotThrow(() -> svc.assertAccountAllowed(k));
        svc.recordAccountFailure(k);
        TooManyRequestsException ex = assertThrows(TooManyRequestsException.class, () -> svc.assertAccountAllowed(k));
        assertEquals(LoginAttemptService.LOCK_SECONDS, ex.getRetryAfterSeconds());
        assertTrue(ex.getMessage().contains("15 minute"), ex.getMessage());
    }

    @Test
    void lockRunsAFullLockPeriodFromTheFifthFailure_notFromTheFirst() {
        String k = LoginAttemptService.userAccountKey(7);
        fail(k, 1);
        assertEquals(LoginAttemptService.WINDOW_SECONDS, ttl.get("login:fail:acct:" + k));
        ttl.put("login:fail:acct:" + k, 30L); // most of the counting window has passed
        fail(k, 4);
        assertEquals(LoginAttemptService.LOCK_SECONDS, ttl.get("login:fail:acct:" + k));
    }

    @Test
    void successfulLoginClearsTheCounter() {
        String k = LoginAttemptService.userAccountKey(7);
        fail(k, 4);
        svc.clearAccount(k);
        fail(k, 4);
        assertDoesNotThrow(() -> svc.assertAccountAllowed(k));
    }

    @Test
    void accountsAreIndependent() {
        fail(LoginAttemptService.userAccountKey(1), 5);
        assertThrows(TooManyRequestsException.class,
                () -> svc.assertAccountAllowed(LoginAttemptService.userAccountKey(1)));
        assertDoesNotThrow(() -> svc.assertAccountAllowed(LoginAttemptService.userAccountKey(2)));
    }

    @Test
    void unknownIdentifiersAreThrottledLikeRealOnes_caseAndSpaceInsensitive() {
        fail(LoginAttemptService.unknownAccountKey("Ghost@Example.com"), 3);
        fail(LoginAttemptService.unknownAccountKey("  ghost@example.COM "), 2);
        assertThrows(TooManyRequestsException.class,
                () -> svc.assertAccountAllowed(LoginAttemptService.unknownAccountKey("ghost@example.com")));
    }

    @Test
    void passwordConfirmationChecksUseAnIndependentCounterFromLogin() {
        String pw = "pw:" + LoginAttemptService.userAccountKey(7);
        fail(pw, 5);
        assertThrows(TooManyRequestsException.class, () -> svc.assertAccountAllowed(pw));
        assertDoesNotThrow(() -> svc.assertAccountAllowed(LoginAttemptService.userAccountKey(7)));
    }

    @Test
    void ipLimit_blocksAfter30Failures_perAddress() {
        for (int i = 0; i < 29; i++) svc.recordIpFailure("203.0.113.9");
        assertDoesNotThrow(() -> svc.assertIpAllowed("203.0.113.9"));
        svc.recordIpFailure("203.0.113.9");
        assertThrows(TooManyRequestsException.class, () -> svc.assertIpAllowed("203.0.113.9"));
        assertDoesNotThrow(() -> svc.assertIpAllowed("198.51.100.1"));
    }

    @Test
    void nullIp_isIgnored() {
        assertDoesNotThrow(() -> {
            svc.recordIpFailure(null);
            svc.assertIpAllowed(null);
        });
    }
}
