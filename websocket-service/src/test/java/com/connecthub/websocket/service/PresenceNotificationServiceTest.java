package com.connecthub.websocket.service;

import com.connecthub.websocket.client.PresenceServiceClient;
import com.connecthub.websocket.dto.PresenceUpdatePayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PresenceNotificationServiceTest {

    @Mock
    private PresenceServiceClient presenceServiceClient;

    @Mock
    private StringRedisTemplate redis;

    private ObjectMapper mapper;
    private PresenceNotificationService service;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        service = new PresenceNotificationService(presenceServiceClient, redis, mapper);
    }

    @Test
    void markOnline_callsPresenceServiceClient_withSessionId() {
        service.markOnline("3", "sess-3");

        verify(presenceServiceClient).markOnline(eq("3"),
                eq(java.util.Map.of("deviceType", "WEB", "sessionId", "sess-3")), eq("3"));
    }

    @Test
    void markOnline_nullSessionId_usesEmptyString() {
        service.markOnline("4", null);

        verify(presenceServiceClient).markOnline(eq("4"), eq(java.util.Map.of("deviceType", "WEB", "sessionId", "")),
                eq("4"));
    }

    @Test
    void markOnline_clientFailure_isHandled() {
        doThrow(new RuntimeException("down")).when(presenceServiceClient).markOnline(any(), any(), any());

        assertDoesNotThrow(() -> service.markOnline("5", "s5"));
    }

    @Test
    void markOffline_callsPresenceServiceClient() {
        service.markOffline("7");

        verify(presenceServiceClient).markOffline("7", "7");
    }

    @Test
    void markOffline_clientFailure_isHandled() {
        doThrow(new RuntimeException("down")).when(presenceServiceClient).markOffline(any(), any());

        assertDoesNotThrow(() -> service.markOffline("8"));
    }

    @Test
    void broadcastOnlineSnapshot_publishesOnlineEventsExceptExcludedUser() throws Exception {
        when(presenceServiceClient.getOnlineUserIds(any())).thenReturn(List.of(10, 11));

        service.broadcastOnlineSnapshot("10");

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(redis, times(1)).convertAndSend(eq("chat:presence"), payloadCaptor.capture());

        PresenceUpdatePayload payload = mapper.readValue(payloadCaptor.getValue(), PresenceUpdatePayload.class);
        assertEquals(11, payload.getUserId());
        assertEquals("ONLINE", payload.getStatus());
    }

    @Test
    void broadcastOnlineSnapshot_failure_isHandled() {
        when(presenceServiceClient.getOnlineUserIds(any())).thenThrow(new RuntimeException("presence down"));

        assertDoesNotThrow(() -> service.broadcastOnlineSnapshot("1"));
        verify(redis, never()).convertAndSend(anyString(), anyString());
    }

    @Test
    void broadcastOnlineSnapshot_threadInterrupted_doesNotThrow() throws InterruptedException {
        // Run the method in a background thread so we can interrupt the Thread.sleep(400) inside it.
        // Interrupting causes the catch(InterruptedException) branch to execute, re-setting the
        // interrupt flag via Thread.currentThread().interrupt() — this covers those two lines.
        CountDownLatch started = new CountDownLatch(1);
        Thread thread = new Thread(() -> {
            started.countDown();
            service.broadcastOnlineSnapshot("99");
        });
        thread.start();
        started.await();
        thread.interrupt(); // triggers InterruptedException inside Thread.sleep(400)
        thread.join(2000);

        assertFalse(thread.isAlive());
        verifyNoInteractions(presenceServiceClient); // interrupted before getOnlineUserIds was reached
    }
}
