package com.connecthub.auth.config;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IpRateLimiterTest {

    @Mock private StringRedisTemplate redis;
    @Mock private ValueOperations<String, String> valueOps;
    @Mock private HttpServletRequest request;

    @InjectMocks private IpRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        lenient().when(redis.opsForValue()).thenReturn(valueOps);
    }

    // ── tryAcquireForgotPassword ───────────────────────────────────────────────

    @Test
    void tryAcquireForgotPassword_withinLimit_returnsTrue() {
        when(request.getHeader("X-Forwarded-For")).thenReturn("10.0.0.1");
        when(valueOps.increment(anyString())).thenReturn(1L);

        assertTrue(rateLimiter.tryAcquireForgotPassword(request));
        verify(redis).expire(anyString(), eq(960L), eq(TimeUnit.SECONDS));
    }

    @Test
    void tryAcquireForgotPassword_atLimit_returnsTrue() {
        when(request.getHeader("X-Forwarded-For")).thenReturn("10.0.0.1");
        when(valueOps.increment(anyString())).thenReturn(5L);

        assertTrue(rateLimiter.tryAcquireForgotPassword(request));
    }

    @Test
    void tryAcquireForgotPassword_exceeded_returnsFalse() {
        when(request.getHeader("X-Forwarded-For")).thenReturn("10.0.0.1");
        when(valueOps.increment(anyString())).thenReturn(6L);

        assertFalse(rateLimiter.tryAcquireForgotPassword(request));
    }

    @Test
    void tryAcquireForgotPassword_noForwardedFor_usesRemoteAddr() {
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("192.168.1.1");
        when(valueOps.increment(anyString())).thenReturn(1L);

        assertTrue(rateLimiter.tryAcquireForgotPassword(request));
    }

    @Test
    void tryAcquireForgotPassword_multipleIpsInXFF_usesFirst() {
        when(request.getHeader("X-Forwarded-For")).thenReturn("1.2.3.4, 5.6.7.8, 9.10.11.12");
        when(valueOps.increment(anyString())).thenReturn(3L);

        boolean result = rateLimiter.tryAcquireForgotPassword(request);

        assertTrue(result);
        // Key should contain the first IP (1.2.3.4), not subsequent ones
        verify(valueOps).increment(argThat(k -> k.contains("1.2.3.4")));
    }

    // ── tryAcquireOtp ─────────────────────────────────────────────────────────

    @Test
    void tryAcquireOtp_withinLimit_returnsTrue() {
        when(request.getHeader("X-Forwarded-For")).thenReturn("10.0.0.2");
        when(valueOps.increment(anyString())).thenReturn(1L);

        assertTrue(rateLimiter.tryAcquireOtp(request));
        verify(redis).expire(anyString(), eq(3660L), eq(TimeUnit.SECONDS));
    }

    @Test
    void tryAcquireOtp_atLimit_returnsTrue() {
        when(request.getHeader("X-Forwarded-For")).thenReturn("10.0.0.2");
        when(valueOps.increment(anyString())).thenReturn(10L);

        assertTrue(rateLimiter.tryAcquireOtp(request));
    }

    @Test
    void tryAcquireOtp_exceeded_returnsFalse() {
        when(request.getHeader("X-Forwarded-For")).thenReturn("10.0.0.2");
        when(valueOps.increment(anyString())).thenReturn(11L);

        assertFalse(rateLimiter.tryAcquireOtp(request));
    }

    // ── getRemainingSeconds ───────────────────────────────────────────────────

    @Test
    void getRemainingSeconds_forgotpw_returnsTtl() {
        when(request.getHeader("X-Forwarded-For")).thenReturn("10.0.0.3");
        when(redis.getExpire(anyString(), eq(TimeUnit.SECONDS))).thenReturn(120L);

        long result = rateLimiter.getRemainingSeconds("forgotpw", request);

        assertEquals(120L, result);
    }

    @Test
    void getRemainingSeconds_otp_returnsTtl() {
        when(request.getHeader("X-Forwarded-For")).thenReturn("10.0.0.3");
        when(redis.getExpire(anyString(), eq(TimeUnit.SECONDS))).thenReturn(500L);

        long result = rateLimiter.getRemainingSeconds("otp", request);

        assertEquals(500L, result);
    }

    @Test
    void getRemainingSeconds_nullTtl_returnsWindowSeconds() {
        when(request.getHeader("X-Forwarded-For")).thenReturn("10.0.0.3");
        when(redis.getExpire(anyString(), eq(TimeUnit.SECONDS))).thenReturn(null);

        long result = rateLimiter.getRemainingSeconds("forgotpw", request);

        assertEquals(900L, result); // FORGOT_PW_WINDOW_SECONDS
    }

    @Test
    void getRemainingSeconds_negativeTtl_returnsWindowSeconds() {
        when(request.getHeader("X-Forwarded-For")).thenReturn("10.0.0.3");
        when(redis.getExpire(anyString(), eq(TimeUnit.SECONDS))).thenReturn(-1L);

        long result = rateLimiter.getRemainingSeconds("otp", request);

        assertEquals(3600L, result); // OTP_WINDOW_SECONDS
    }
}
