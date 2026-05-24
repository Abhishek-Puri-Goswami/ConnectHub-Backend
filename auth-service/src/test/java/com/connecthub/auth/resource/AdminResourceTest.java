package com.connecthub.auth.resource;

import com.connecthub.auth.dto.AnnouncementDto;
import com.connecthub.auth.entity.AnalyticsSnapshot;
import com.connecthub.auth.entity.Announcement;
import com.connecthub.auth.entity.AuditLog;
import com.connecthub.auth.entity.User;
import com.connecthub.auth.repository.AnnouncementRepository;
import com.connecthub.auth.service.AnalyticsService;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminResourceTest {

    @Mock private AuthService authService;
    @Mock private AuditService auditService;
    @Mock private AnalyticsService analyticsService;
    @Mock private StringRedisTemplate redis;
    @Mock private AnnouncementRepository announcementRepository;

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

    @Test
    void getAnalytics_returnsSnapshots() {
        when(analyticsService.getRecentSnapshots()).thenReturn(List.of(new AnalyticsSnapshot()));
        ResponseEntity<List<AnalyticsSnapshot>> res = adminResource.getAnalytics();
        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertNotNull(res.getBody());
    }

    @Test
    void createAnnouncement_success() {
        User admin = User.builder().userId(1).username("admin").fullName("Admin User").build();
        when(authService.getUserById(1)).thenReturn(admin);
        Announcement saved = Announcement.builder()
                .id(10L).content("Test announcement").adminId(1).adminName("Admin User")
                .createdAt(LocalDateTime.now()).build();
        when(announcementRepository.save(any())).thenReturn(saved);

        AnnouncementDto req = new AnnouncementDto();
        req.setContent("Test announcement");

        ResponseEntity<AnnouncementDto> res = adminResource.createAnnouncement(1, "ADMIN", req);

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertNotNull(res.getBody());
        assertEquals(10L, res.getBody().getId());
        verify(announcementRepository).save(any());
    }

    @Test
    void createAnnouncement_platformAdmin_success() {
        User admin = User.builder().userId(2).username("padmin").fullName(null).build();
        when(authService.getUserById(2)).thenReturn(admin);
        Announcement saved = Announcement.builder()
                .id(11L).content("Platform msg").adminId(2).adminName("padmin")
                .createdAt(LocalDateTime.now()).build();
        when(announcementRepository.save(any())).thenReturn(saved);

        AnnouncementDto req = new AnnouncementDto();
        req.setContent("Platform msg");

        ResponseEntity<AnnouncementDto> res = adminResource.createAnnouncement(2, "PLATFORM_ADMIN", req);

        assertEquals(HttpStatus.OK, res.getStatusCode());
    }

    @Test
    void createAnnouncement_nonAdmin_forbidden() {
        AnnouncementDto req = new AnnouncementDto();
        req.setContent("Msg");

        ResponseEntity<AnnouncementDto> res = adminResource.createAnnouncement(1, "USER", req);

        assertEquals(HttpStatus.FORBIDDEN, res.getStatusCode());
        verify(announcementRepository, never()).save(any());
    }

    @Test
    void getAnnouncements_returnsList() {
        Announcement a = Announcement.builder()
                .id(1L).title("Hello").content("World").adminId(1).adminName("Admin")
                .createdAt(LocalDateTime.now()).build();
        when(announcementRepository.findTop50ByOrderByCreatedAtDesc()).thenReturn(List.of(a));

        ResponseEntity<List<AnnouncementDto>> res = adminResource.getAnnouncements();

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertEquals(1, res.getBody().size());
        assertEquals("Hello", res.getBody().get(0).getTitle());
    }

    @Test
    void getAnnouncements_empty() {
        when(announcementRepository.findTop50ByOrderByCreatedAtDesc()).thenReturn(List.of());
        ResponseEntity<List<AnnouncementDto>> res = adminResource.getAnnouncements();
        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertEquals(0, res.getBody().size());
    }
}
