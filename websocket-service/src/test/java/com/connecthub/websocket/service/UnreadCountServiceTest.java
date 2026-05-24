package com.connecthub.websocket.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UnreadCountServiceTest {

    @Mock StringRedisTemplate redis;
    @Mock ValueOperations<String, String> valueOps;

    @InjectMocks UnreadCountService unreadCountService;

    @Test
    void increment_callsRedisIncrement() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment("unread:1:room42")).thenReturn(3L);
        unreadCountService.increment(1, "room42");
        verify(valueOps).increment("unread:1:room42");
    }

    @Test
    void reset_deletesKey() {
        unreadCountService.reset(1, "room42");
        verify(redis).delete("unread:1:room42");
    }

    @Test
    void getCount_whenKeyExists_returnsCount() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("unread:1:room42")).thenReturn("7");
        assertThat(unreadCountService.getCount(1, "room42")).isEqualTo(7L);
    }

    @Test
    void getCount_whenKeyAbsent_returnsZero() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("unread:1:room42")).thenReturn(null);
        assertThat(unreadCountService.getCount(1, "room42")).isEqualTo(0L);
    }

    @Test
    void getCount_whenValueMalformed_returnsZero() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("unread:1:room42")).thenReturn("not-a-number");
        assertThat(unreadCountService.getCount(1, "room42")).isEqualTo(0L);
    }

    // ── getAllForUser ─────────────────────────────────────────────────────────

    @Test
    void getAllForUser_noKeys_returnsEmptyMap() {
        when(redis.keys("unread:5:*")).thenReturn(Set.of());

        Map<String, Long> result = unreadCountService.getAllForUser(5);

        assertThat(result).isEmpty();
    }

    @Test
    void getAllForUser_nullKeys_returnsEmptyMap() {
        when(redis.keys("unread:5:*")).thenReturn(null);

        Map<String, Long> result = unreadCountService.getAllForUser(5);

        assertThat(result).isEmpty();
    }

    @Test
    void getAllForUser_withCounts_returnsRoomCountMap() {
        when(redis.keys("unread:5:*")).thenReturn(Set.of("unread:5:room1", "unread:5:room2"));
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("unread:5:room1")).thenReturn("3");
        when(valueOps.get("unread:5:room2")).thenReturn("0");  // zero should be excluded

        Map<String, Long> result = unreadCountService.getAllForUser(5);

        assertThat(result).containsEntry("room1", 3L);
        assertThat(result).doesNotContainKey("room2");
    }

    @Test
    void getAllForUser_malformedValue_skipsKey() {
        when(redis.keys("unread:5:*")).thenReturn(Set.of("unread:5:roomX"));
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("unread:5:roomX")).thenReturn("bad");

        Map<String, Long> result = unreadCountService.getAllForUser(5);

        assertThat(result).isEmpty();
    }

    @Test
    void getAllForUser_nullValue_skipsKey() {
        when(redis.keys("unread:5:*")).thenReturn(Set.of("unread:5:roomY"));
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("unread:5:roomY")).thenReturn(null);

        Map<String, Long> result = unreadCountService.getAllForUser(5);

        assertThat(result).isEmpty();
    }
}
