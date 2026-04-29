package com.connecthub.auth.config;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * IpRateLimiter — Redis-backed IP-based rate limiting for sensitive auth endpoints.
 *
 * PURPOSE:
 *   Protects forgot-password and OTP request endpoints from brute-force abuse
 *   by limiting requests per IP address. This is separate from the per-user
 *   rate limiter in the gateway because these endpoints are unauthenticated
 *   (no X-User-Id available) — the IP is the only identifier.
 *
 * KEY STRUCTURE:
 *   "ip:ratelimit:{action}:{ip}:{timeBucket}"
 *   The time bucket groups requests into fixed windows:
 *   - forgotpw: 15-minute windows (epochSecond / 900)
 *   - otp:      1-hour windows (epochSecond / 3600)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class IpRateLimiter {

    private final StringRedisTemplate redis;

    private static final int FORGOT_PW_LIMIT = 5;
    private static final int FORGOT_PW_WINDOW_SECONDS = 900; // 15 minutes

    private static final int OTP_LIMIT = 10;
    private static final int OTP_WINDOW_SECONDS = 3600; // 1 hour

    /**
     * tryAcquireForgotPassword — allows up to 5 requests per IP per 15 minutes.
     * @return true if within limit, false if rate limited
     */
    public boolean tryAcquireForgotPassword(HttpServletRequest request) {
        String ip = extractClientIp(request);
        long bucket = System.currentTimeMillis() / 1000 / FORGOT_PW_WINDOW_SECONDS;
        String key = "ip:ratelimit:forgotpw:" + ip + ":" + bucket;
        return checkLimit(key, FORGOT_PW_LIMIT, FORGOT_PW_WINDOW_SECONDS);
    }

    /**
     * tryAcquireOtp — allows up to 10 OTP requests per IP per hour.
     * @return true if within limit, false if rate limited
     */
    public boolean tryAcquireOtp(HttpServletRequest request) {
        String ip = extractClientIp(request);
        long bucket = System.currentTimeMillis() / 1000 / OTP_WINDOW_SECONDS;
        String key = "ip:ratelimit:otp:" + ip + ":" + bucket;
        return checkLimit(key, OTP_LIMIT, OTP_WINDOW_SECONDS);
    }

    /**
     * getRemainingSeconds — returns the approximate seconds until the rate limit window resets.
     */
    public long getRemainingSeconds(String action, HttpServletRequest request) {
        int windowSeconds = "forgotpw".equals(action) ? FORGOT_PW_WINDOW_SECONDS : OTP_WINDOW_SECONDS;
        String ip = extractClientIp(request);
        long bucket = System.currentTimeMillis() / 1000 / windowSeconds;
        String key = "ip:ratelimit:" + action + ":" + ip + ":" + bucket;
        Long ttl = redis.getExpire(key, TimeUnit.SECONDS);
        return ttl != null && ttl > 0 ? ttl : windowSeconds;
    }

    private boolean checkLimit(String key, int limit, int windowSeconds) {
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1) {
            redis.expire(key, windowSeconds + 60, TimeUnit.SECONDS);
        }
        return count != null && count <= limit;
    }

    /**
     * extractClientIp — reads the client IP from X-Forwarded-For (set by the gateway)
     * falling back to the remote address from the servlet request.
     */
    private String extractClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            // X-Forwarded-For may contain multiple IPs; the first one is the real client
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
