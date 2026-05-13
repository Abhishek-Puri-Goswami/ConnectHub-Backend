package com.connecthub.auth.resource;

import com.connecthub.auth.entity.AuditLog;
import com.connecthub.auth.entity.User;
import com.connecthub.auth.service.AuditService;
import com.connecthub.auth.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminResourceTest {

    @Mock
    private AuthService authService;

    @Mock
    private AuditService auditService;

    @Mock
    private StringRedisTemplate redis;

    @InjectMocks
    private AdminResource adminResource;

    @Test
    @SuppressWarnings("unchecked")
    void suspend_asAdmin() {
        int targetId = 1;
        User target = User.builder().userId(targetId).role("USER").username("test").build();
        when(authService.getUserById(targetId)).thenReturn(target);
        when(authService.suspendUser(targetId)).thenReturn(target);
        
        ValueOperations<String, String> valOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valOps);
        
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getRemoteAddr()).thenReturn("127.0.0.1");

        ResponseEntity<User> res = adminResource.suspend(targetId, 100, "ADMIN", req);

        assertEquals(HttpStatus.OK, res.getStatusCode());
        verify(redis).convertAndSend(anyString(), anyString());
        verify(auditService).log(eq(100), eq("USER_SUSPEND"), eq("USER"), eq("1"), anyString(), eq("127.0.0.1"));
    }

    @Test
    void suspend_privilegedTarget_forbidden() {
        int targetId = 1;
        User target = User.builder().userId(targetId).role("PLATFORM_ADMIN").build();
        when(authService.getUserById(targetId)).thenReturn(target);

        ResponseEntity<User> res = adminResource.suspend(targetId, 100, "ADMIN", mock(HttpServletRequest.class));

        assertEquals(HttpStatus.FORBIDDEN, res.getStatusCode());
    }

    @Test
    void reactivate_success() {
        int targetId = 1;
        User target = User.builder().userId(targetId).role("USER").username("test").build();
        when(authService.getUserById(targetId)).thenReturn(target);
        when(authService.reactivateUser(targetId)).thenReturn(target);
        
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getRemoteAddr()).thenReturn("127.0.0.1");

        ResponseEntity<User> res = adminResource.reactivate(targetId, 100, "ADMIN", req);

        assertEquals(HttpStatus.OK, res.getStatusCode());
        verify(redis, times(2)).delete(anyString());
    }

    @Test
    void delete_success() {
        int targetId = 1;
        User target = User.builder().userId(targetId).role("USER").username("test").build();
        when(authService.getUserById(targetId)).thenReturn(target);
        
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getRemoteAddr()).thenReturn("127.0.0.1");

        ResponseEntity<Void> res = adminResource.delete(targetId, 100, "ADMIN", req);

        assertEquals(HttpStatus.NO_CONTENT, res.getStatusCode());
        verify(authService).deleteUser(targetId);
    }

    @Test
    void changeRole_onlyPlatformAdmin() {
        ResponseEntity<User> res = adminResource.changeRole(1, Map.of("role", "ADMIN"), 100, "ADMIN", mock(HttpServletRequest.class));
        assertEquals(HttpStatus.FORBIDDEN, res.getStatusCode());

        User u = new User();
        when(authService.changeRole(1, "ADMIN")).thenReturn(u);
        res = adminResource.changeRole(1, Map.of("role", "ADMIN"), 100, "PLATFORM_ADMIN", mock(HttpServletRequest.class));
        assertEquals(HttpStatus.OK, res.getStatusCode());
    }

    @Test
    @SuppressWarnings("unchecked")
    void getAuditLogs() {
        Page<AuditLog> page = mock(Page.class);
        when(auditService.getLogs(0, 50)).thenReturn(page);
        assertEquals(HttpStatus.OK, adminResource.getAuditLogs(0, 50).getStatusCode());
    }

    @Test
    void getAllUsers() {
        when(authService.getAllUsers()).thenReturn(List.of());
        assertEquals(HttpStatus.OK, adminResource.getAllUsers().getStatusCode());
    }
}
