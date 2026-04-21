package com.connecthub.websocket.service;

import com.connecthub.websocket.client.RoomServiceClient;
import com.connecthub.websocket.dto.ChatMessagePayload;
import com.connecthub.websocket.dto.RoomMemberDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeliveryServiceTest {

    @Mock private SimpMessagingTemplate messaging;
    @Mock private StringRedisTemplate redis;
    @Mock private ObjectMapper mapper;
    @Mock private RoomServiceClient roomServiceClient;
    @Mock private KafkaTemplate<String, Object> kafkaTemplate;
    @Mock private UnreadCountService unreadCountService;
    @Mock private SetOperations<String, String> setOps;
    @Mock private ListOperations<String, String> listOps;

    @InjectMocks
    private DeliveryService deliveryService;

    @BeforeEach
    void setUp() {
        lenient().when(redis.opsForSet()).thenReturn(setOps);
        lenient().when(redis.opsForList()).thenReturn(listOps);
    }

    @Test
    void deliverToRoomMembers_onlineUser() {
        ChatMessagePayload msg = new ChatMessagePayload();
        msg.setRoomId("r1");
        msg.setSenderId(1);

        RoomMemberDto member2 = new RoomMemberDto();
        member2.setUserId(2);

        when(roomServiceClient.getRoomMembers("r1", "1")).thenReturn(List.of(member2));
        when(setOps.isMember("presence:online", "2")).thenReturn(true);

        deliveryService.deliverToRoomMembers(msg);

        verify(unreadCountService).increment(2, "r1");
        verify(messaging).convertAndSendToUser(eq("2"), eq("/queue/messages"), eq(msg));
        verify(listOps, never()).rightPush(anyString(), anyString());
    }

    @Test
    void deliverToRoomMembers_offlineUser() throws JsonProcessingException {
        ChatMessagePayload msg = new ChatMessagePayload();
        msg.setRoomId("r1");
        msg.setSenderId(1);
        msg.setContent("hello");

        RoomMemberDto member2 = new RoomMemberDto();
        member2.setUserId(2);

        when(roomServiceClient.getRoomMembers("r1", "1")).thenReturn(List.of(member2));
        when(setOps.isMember("presence:online", "2")).thenReturn(false);
        when(mapper.writeValueAsString(msg)).thenReturn("{}");

        deliveryService.deliverToRoomMembers(msg);

        verify(unreadCountService).increment(2, "r1");
        verify(messaging).convertAndSendToUser(eq("2"), eq("/queue/messages"), eq(msg));
        verify(listOps).rightPush("pending:messages:2", "{}");
        verify(redis).expire("pending:messages:2", 7, TimeUnit.DAYS);
        verify(kafkaTemplate).send(eq("notifications.offline"), any());
    }

    @Test
    void flushPendingMessages() throws JsonProcessingException {
        when(listOps.size("pending:messages:2")).thenReturn(2L);
        when(listOps.leftPop("pending:messages:2")).thenReturn("{}", "{}", null);
        when(mapper.readValue("{}", ChatMessagePayload.class)).thenReturn(new ChatMessagePayload());

        deliveryService.flushPendingMessages("2");

        verify(messaging, times(2)).convertAndSendToUser(eq("2"), eq("/queue/messages"), any(ChatMessagePayload.class));
    }

    @Test
    void updateRoomTimestamp() {
        deliveryService.updateRoomTimestamp("r1", "1");
        verify(roomServiceClient).updateLastMessageAt("r1", "1");
    }

    @Test
    void persistLastRead() {
        deliveryService.persistLastRead("r1", "1");
        verify(roomServiceClient).updateLastRead("r1", "1", "1");
    }

    @Test
    void flushPendingMessagesWithDelay() {
        when(listOps.size("pending:messages:2")).thenReturn(0L);
        deliveryService.flushPendingMessagesWithDelay("2");
        verify(listOps).size("pending:messages:2");
    }

    @Test
    void pushNotification() {
        Map<String, Object> notif = Map.of("id", "1");
        deliveryService.pushNotification(2, notif);
        verify(messaging).convertAndSendToUser("2", "/queue/notifications", notif);
    }
}
