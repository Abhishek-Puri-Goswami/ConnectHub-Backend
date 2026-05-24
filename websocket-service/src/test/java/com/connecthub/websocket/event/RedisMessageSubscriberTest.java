package com.connecthub.websocket.event;

import com.connecthub.websocket.config.RedisConfig;
import com.connecthub.websocket.dto.*;
import com.connecthub.websocket.service.DeliveryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedisMessageSubscriberTest {

    @Mock private SimpMessagingTemplate messaging;
    @Mock private ObjectMapper objectMapper;
    @Mock private DeliveryService deliveryService;

    @InjectMocks private RedisMessageSubscriber subscriber;

    private org.springframework.data.redis.connection.Message msg(String channel, String body) {
        return new DefaultMessage(channel.getBytes(), body.getBytes());
    }

    // ── CHAT channel ─────────────────────────────────────────────────────────

    @Test
    void chatChannel_forwardsToRoomTopic() throws Exception {
        ChatMessagePayload p = new ChatMessagePayload();
        p.setRoomId("r1"); p.setContent("Hi");
        when(objectMapper.readValue(any(byte[].class), eq(ChatMessagePayload.class))).thenReturn(p);

        subscriber.onMessage(msg(RedisConfig.CHAT_CHANNEL, "{}"), null);

        verify(messaging).convertAndSend(eq("/topic/room/r1"), any(ChatMessagePayload.class));
        verify(deliveryService).deliverToRoomMembers(p);
    }

    // ── PRESENCE channel ─────────────────────────────────────────────────────

    @Test
    void presenceChannel_forwardsToPresenceTopic() throws Exception {
        PresenceUpdatePayload p = new PresenceUpdatePayload();
        p.setUserId(5); p.setStatus("ONLINE");
        when(objectMapper.readValue(any(byte[].class), eq(PresenceUpdatePayload.class))).thenReturn(p);

        subscriber.onMessage(msg(RedisConfig.PRESENCE_CHANNEL, "{}"), null);

        verify(messaging).convertAndSend(eq("/topic/presence"), any(PresenceUpdatePayload.class));
    }

    // ── EDIT channel ─────────────────────────────────────────────────────────

    @Test
    void editChannel_forwardsToRoomEditTopic() throws Exception {
        MessageEditPayload p = new MessageEditPayload();
        p.setRoomId("r2"); p.setMessageId("m1"); p.setNewContent("updated");
        when(objectMapper.readValue(any(byte[].class), eq(MessageEditPayload.class))).thenReturn(p);

        subscriber.onMessage(msg(RedisConfig.EDIT_CHANNEL, "{}"), null);

        verify(messaging).convertAndSend(eq("/topic/room/r2/edit"), any(MessageEditPayload.class));
    }

    // ── DELETE channel ───────────────────────────────────────────────────────

    @Test
    void deleteChannel_forwardsToRoomDeleteTopic() throws Exception {
        MessageDeletePayload p = new MessageDeletePayload();
        p.setRoomId("r3"); p.setMessageId("m2");
        when(objectMapper.readValue(any(byte[].class), eq(MessageDeletePayload.class))).thenReturn(p);

        subscriber.onMessage(msg(RedisConfig.DELETE_CHANNEL, "{}"), null);

        verify(messaging).convertAndSend(eq("/topic/room/r3/delete"), any(MessageDeletePayload.class));
    }

    // ── REACTION channel ─────────────────────────────────────────────────────

    @Test
    void reactionChannel_forwardsToRoomReactionsTopic() throws Exception {
        ReactionPayload p = new ReactionPayload();
        p.setRoomId("r4"); p.setMessageId("m3"); p.setEmoji("👍");
        when(objectMapper.readValue(any(byte[].class), eq(ReactionPayload.class))).thenReturn(p);

        subscriber.onMessage(msg(RedisConfig.REACTION_CHANNEL, "{}"), null);

        verify(messaging).convertAndSend(eq("/topic/room/r4/reactions"), any(ReactionPayload.class));
    }

    // ── NOTIF channel ─────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    @Test
    void notifChannel_withRecipientId_pushesNotificationToUser() throws Exception {
        Map<String, Object> notif = Map.of("recipientId", 7, "type", "MENTION");
        when(objectMapper.readValue(any(byte[].class), eq(Map.class))).thenReturn(notif);

        subscriber.onMessage(msg(RedisConfig.NOTIF_CHANNEL, "{}"), null);

        verify(deliveryService).pushNotification(eq(7), eq(notif));
    }

    @SuppressWarnings("unchecked")
    @Test
    void notifChannel_withoutRecipientId_noAction() throws Exception {
        Map<String, Object> notif = Map.of("type", "MENTION");
        when(objectMapper.readValue(any(byte[].class), eq(Map.class))).thenReturn(notif);

        subscriber.onMessage(msg(RedisConfig.NOTIF_CHANNEL, "{}"), null);

        verify(deliveryService, never()).pushNotification(anyInt(), any());
    }

    // ── PIN channel ───────────────────────────────────────────────────────────

    @Test
    void pinChannel_forwardsToRoomPinTopic() throws Exception {
        PinMessagePayload p = new PinMessagePayload("r5", "m9", 1, System.currentTimeMillis());
        when(objectMapper.readValue(any(byte[].class), eq(PinMessagePayload.class))).thenReturn(p);

        subscriber.onMessage(msg(RedisConfig.PIN_CHANNEL, "{}"), null);

        verify(messaging).convertAndSend(eq("/topic/room/r5/pin"), any(PinMessagePayload.class));
    }

    // ── READ channel ──────────────────────────────────────────────────────────

    @Test
    void readChannel_forwardsToRoomReadTopic() throws Exception {
        ReadReceiptPayload p = new ReadReceiptPayload(1, "r6", "m10");
        when(objectMapper.readValue(any(byte[].class), eq(ReadReceiptPayload.class))).thenReturn(p);

        subscriber.onMessage(msg(RedisConfig.READ_CHANNEL, "{}"), null);

        verify(messaging).convertAndSend(eq("/topic/room/r6/read"), any(ReadReceiptPayload.class));
    }

    // ── DELIVERY_ACK channel ──────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    @Test
    void deliveryAckChannel_withSenderId_sendsToUser() throws Exception {
        Map<String, Object> ack = Map.of("senderId", 3, "messageId", "m11", "status", "DELIVERED");
        when(objectMapper.readValue(any(byte[].class), eq(Map.class))).thenReturn(ack);

        subscriber.onMessage(msg(RedisConfig.DELIVERY_ACK_CHANNEL, "{}"), null);

        verify(messaging).convertAndSendToUser(eq("3"), eq("/queue/delivery-ack"), eq(ack));
    }

    @SuppressWarnings("unchecked")
    @Test
    void deliveryAckChannel_withoutSenderId_noAction() throws Exception {
        Map<String, Object> ack = Map.of("messageId", "m11", "status", "DELIVERED");
        when(objectMapper.readValue(any(byte[].class), eq(Map.class))).thenReturn(ack);

        subscriber.onMessage(msg(RedisConfig.DELIVERY_ACK_CHANNEL, "{}"), null);

        verify(messaging, never()).convertAndSendToUser(anyString(), anyString(), any());
    }

    // ── SUSPENDED channel ─────────────────────────────────────────────────────

    @Test
    void suspendedChannel_sendsAccountSuspendedToUser() {
        subscriber.onMessage(msg(RedisConfig.SUSPENDED_CHANNEL, "99"), null);

        verify(messaging).convertAndSendToUser(
                eq("99"),
                eq("/queue/notifications"),
                argThat(o -> o instanceof Map && "ACCOUNT_SUSPENDED".equals(((Map<?, ?>) o).get("type")))
        );
    }

    // ── Unknown channel ──────────────────────────────────────────────────────

    @Test
    void unknownChannel_noMessageSent() throws Exception {
        subscriber.onMessage(msg("chat:unknown", "{}"), null);
        verifyNoInteractions(messaging);
        verifyNoInteractions(deliveryService);
    }

    // ── Deserialisation error ────────────────────────────────────────────────

    @Test
    void chatChannel_deserializationError_doesNotThrow() throws Exception {
        when(objectMapper.readValue(any(byte[].class), eq(ChatMessagePayload.class)))
            .thenThrow(new RuntimeException("bad json"));

        assertDoesNotThrow(() -> subscriber.onMessage(msg(RedisConfig.CHAT_CHANNEL, "bad"), null));
        verifyNoInteractions(messaging);
    }
}
