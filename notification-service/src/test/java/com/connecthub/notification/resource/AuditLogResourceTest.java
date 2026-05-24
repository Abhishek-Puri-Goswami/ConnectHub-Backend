package com.connecthub.notification.resource;

import com.connecthub.notification.entity.AuditLog;
import com.connecthub.notification.repository.AuditLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditLogResourceTest {

    @Mock private AuditLogRepository auditLogRepository;
    @InjectMocks private AuditLogResource resource;

    @Test
    void getAuditLogs_defaultPagination_returnsMappedPage() {
        AuditLog log = new AuditLog();
        Page<AuditLog> page = new PageImpl<>(List.of(log), PageRequest.of(0, 50), 1);
        when(auditLogRepository.findAllByOrderByCreatedAtDesc(any())).thenReturn(page);

        Page<AuditLog> result = resource.getAuditLogs(0, 50);

        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    void getAuditLogs_customPageSize_passesCorrectPageable() {
        Page<AuditLog> emptyPage = Page.empty();
        when(auditLogRepository.findAllByOrderByCreatedAtDesc(any())).thenReturn(emptyPage);

        Page<AuditLog> result = resource.getAuditLogs(2, 10);

        assertThat(result).isEmpty();
    }
}
