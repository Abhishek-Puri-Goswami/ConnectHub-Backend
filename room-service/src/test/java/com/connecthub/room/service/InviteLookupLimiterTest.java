package com.connecthub.room.service;

import com.connecthub.room.exception.TooManyRequestsException;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class InviteLookupLimiterTest {

    @Test
    @SuppressWarnings("unchecked")
    void twentyLookupsPerMinuteAreFine_theTwentyFirstIsRefused() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        AtomicLong n = new AtomicLong();
        when(ops.increment(anyString())).thenAnswer(inv -> n.incrementAndGet());
        InviteLookupLimiter limiter = new InviteLookupLimiter(redis);

        for (int i = 0; i < InviteLookupLimiter.MAX_PER_MINUTE; i++) limiter.check(5);
        TooManyRequestsException ex = assertThrows(TooManyRequestsException.class, () -> limiter.check(5));
        assertTrue(ex.getRetryAfterSeconds() >= 1 && ex.getRetryAfterSeconds() <= 60);
    }
}
