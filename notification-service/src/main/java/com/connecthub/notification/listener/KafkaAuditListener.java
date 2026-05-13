package com.connecthub.notification.listener;

import com.connecthub.notification.entity.AuditLog;
import com.connecthub.notification.repository.AuditLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Consumes audit events published by auth-service and persists them to the
 * audit_logs table in notification-service's database.
 *
 * Reliability is inherited from KafkaConfig: 3 retries with 2 s backoff,
 * then dead-lettered to audit.events.dlq for manual investigation.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class KafkaAuditListener {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    @SneakyThrows
    @KafkaListener(
            topics = "audit.events",
            groupId = "notification-audit-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void processAuditEvent(
            @Payload String messageJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {

        log.debug("Consuming {}[{}]@{}", topic, partition, offset);

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = objectMapper.readValue(messageJson, Map.class);

        AuditLog entry = AuditLog.builder()
                .actorId(((Number) payload.get("actorId")).intValue())
                .action((String) payload.get("action"))
                .entityType((String) payload.get("entityType"))
                .entityId((String) payload.get("entityId"))
                .details((String) payload.get("details"))
                .ipAddress((String) payload.get("ipAddress"))
                .build();

        auditLogRepository.save(entry);

        log.info("Audit persisted: actor={} action={} from {}[{}]@{}",
                entry.getActorId(), entry.getAction(), topic, partition, offset);
    }

    @KafkaListener(
            topics = "audit.events.dlq",
            groupId = "notification-audit-dlq-group"
    )
    public void handleDlq(
            @Payload String messageJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.OFFSET) long offset) {
        log.error("DLQ MESSAGE at {}@{}: {} — manual intervention required", topic, offset, messageJson);
    }
}
