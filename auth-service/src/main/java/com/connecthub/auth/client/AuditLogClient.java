package com.connecthub.auth.client;

import com.connecthub.auth.dto.RestPage;
import com.connecthub.auth.entity.AuditLog;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "notification-service")
public interface AuditLogClient {

    @GetMapping("/internal/audit-logs")
    RestPage<AuditLog> getAuditLogs(@RequestParam int page, @RequestParam int size);
}
