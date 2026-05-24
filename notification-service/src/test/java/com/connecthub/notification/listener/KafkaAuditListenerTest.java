package com.connecthub.notification.listener;

import com.connecthub.notification.repository.AuditLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KafkaAuditListenerTest {

    @Mock private AuditLogRepository auditLogRepository;

    @InjectMocks private KafkaAuditListener listener;

    // Use real ObjectMapper for JSON deserialization
    private final ObjectMapper realMapper = new ObjectMapper();

    @Test
    void processAuditEvent_validPayload_savesAuditLog() throws Exception {
        // Replace mock ObjectMapper with real one via reflection
        org.springframework.test.util.ReflectionTestUtils.setField(listener, "objectMapper", realMapper);

        String json = "{\"actorId\":1,\"action\":\"LOGIN\",\"entityType\":\"USER\"," +
                "\"entityId\":\"42\",\"details\":\"Success\",\"ipAddress\":\"127.0.0.1\"}";

        listener.processAuditEvent(json, "audit.events", 0, 100L);

        var cap = ArgumentCaptor.forClass(com.connecthub.notification.entity.AuditLog.class);
        verify(auditLogRepository).save(cap.capture());
        assertEquals(1, cap.getValue().getActorId());
        assertEquals("LOGIN", cap.getValue().getAction());
        assertEquals("USER", cap.getValue().getEntityType());
        assertEquals("42", cap.getValue().getEntityId());
        assertEquals("127.0.0.1", cap.getValue().getIpAddress());
    }

    @Test
    void handleDlq_logsMessageWithoutSaving() {
        listener.handleDlq("bad-json", "audit.events.dlq", 50L);

        verifyNoInteractions(auditLogRepository);
    }
}
