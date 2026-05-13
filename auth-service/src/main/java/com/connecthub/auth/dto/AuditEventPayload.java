package com.connecthub.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditEventPayload {
    private int actorId;
    private String action;
    private String entityType;
    private String entityId;
    private String details;
    private String ipAddress;
}
