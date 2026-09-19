package com.connecthub.room.service;

import com.connecthub.room.exception.TooManyRequestsException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Invite codes are short (8 hex characters), so lookups by code are rate-limited per user: 20 per minute across
 * preview and join together. Enough for normal use, far too slow to enumerate the code space.
 */
@Component
@RequiredArgsConstructor
public class InviteLookupLimiter {

    static final int MAX_PER_MINUTE = 20;

    private final StringRedisTemplate redis;

    public void check(int userId) {
        long minute = System.currentTimeMillis() / 60_000;
        String key = "ratelimit:invite:" + userId + ":" + minute;
        Long n = redis.opsForValue().increment(key);
        if (n != null && n == 1) redis.expire(key, 2, TimeUnit.MINUTES);
        if (n != null && n > MAX_PER_MINUTE) {
            long retry = 60 - (System.currentTimeMillis() / 1000 % 60);
            throw new TooManyRequestsException("Too many invite lookups. Try again in a minute.", retry);
        }
    }
}
