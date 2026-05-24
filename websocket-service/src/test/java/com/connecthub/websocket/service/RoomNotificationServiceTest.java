package com.connecthub.websocket.service;

import com.connecthub.websocket.config.RedisConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoomNotificationServiceTest {

    @Mock
    private SimpMessagingTemplate messaging;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private StringRedisTemplate redis;

    @InjectMocks
    private RoomNotificationService roomNotificationService;

    @Test
    void processRoomCreated_sendsToAllMembers() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("memberIds", List.of(1, 2));

        when(objectMapper.readValue("{}", Map.class)).thenReturn(payload);
        when(objectMapper.convertValue(payload, Map.class)).thenReturn(payload);
        when(objectMapper.writeValueAsString(any(Map.class))).thenReturn("{}");

        roomNotificationService.processRoomCreated("{}", "room.created", 0);

        // processRoomCreated now routes through Redis pub/sub (NOTIF_CHANNEL) for cross-pod delivery,
        // one publish per member (2 members → 2 publishes)
        verify(redis, times(2)).convertAndSend(eq(RedisConfig.NOTIF_CHANNEL), anyString());
    }

    @Test
    void processRoomCreated_invalidPayload_aborts() throws Exception {
        when(objectMapper.readValue("inv", Map.class)).thenThrow(new RuntimeException("err"));

        roomNotificationService.processRoomCreated("inv", "room.created", 0);

        verify(redis, never()).convertAndSend(anyString(), anyString());
    }
}
