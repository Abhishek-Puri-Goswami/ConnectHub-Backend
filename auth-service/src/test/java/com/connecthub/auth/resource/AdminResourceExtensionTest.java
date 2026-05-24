package com.connecthub.auth.resource;

import com.connecthub.auth.dto.AnnouncementDto;
import com.connecthub.auth.entity.Announcement;
import com.connecthub.auth.entity.AnalyticsSnapshot;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminResourceExtensionTest {

    @Mock private AuthService authService;
    @Mock private AuditService auditService;
    @Mock private AnalyticsService analyticsService;
    @Mock private StringRedisTemplate redis;
    @Mock private AnnouncementRepository announcementRepository;

    @InjectMocks private AdminResource adminResource;

    // ── getAnalytics ──────────────────────────────────────────────────────────

    @Test
    void getAnalytics_delegatesToService() {
        List<AnalyticsSnapshot> snapshots = List.of(new AnalyticsSnapshot());
        when(analyticsService.getRecentSnapshots()).thenReturn(snapshots);

        ResponseEntity<List<AnalyticsSnapshot>> resp = adminResource.getAnalytics();

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertSame(snapshots, resp.getBody());
    }

    // ── createAnnouncement ────────────────────────────────────────────────────

    @Test
    void createAnnouncement_nonAdmin_returns403() {
        AnnouncementDto req = AnnouncementDto.builder().title("t").content("c").build();

        ResponseEntity<AnnouncementDto> resp = adminResource.createAnnouncement(1, "USER", req);

        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
        verifyNoInteractions(announcementRepository);
    }

    @Test
    void createAnnouncement_blankContent_returns400() {
        AnnouncementDto req = AnnouncementDto.builder().title("t").content("   ").build();

        ResponseEntity<AnnouncementDto> resp = adminResource.createAnnouncement(1, "ADMIN", req);

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        verifyNoInteractions(announcementRepository);
    }

    @Test
    void createAnnouncement_nullContent_returns400() {
        AnnouncementDto req = AnnouncementDto.builder().title("t").build(); // content null

        ResponseEntity<AnnouncementDto> resp = adminResource.createAnnouncement(1, "PLATFORM_ADMIN", req);

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
    }

    @Test
    void createAnnouncement_validRequest_savesAndReturns200() {
        AnnouncementDto req = AnnouncementDto.builder().title("Hello").content("World").build();
        User admin = User.builder().userId(1).username("admin").fullName("Admin User").build();
        Announcement saved = Announcement.builder().id(1L).title("Hello").content("World")
                .adminId(1).adminName("Admin User").build();
        when(authService.getUserById(1)).thenReturn(admin);
        when(announcementRepository.save(any())).thenReturn(saved);

        ResponseEntity<AnnouncementDto> resp = adminResource.createAnnouncement(1, "ADMIN", req);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertNotNull(resp.getBody());
        assertEquals("Hello", resp.getBody().getTitle());
    }

    // ── getAnnouncements ──────────────────────────────────────────────────────

    @Test
    void getAnnouncements_returnsAllMapped() {
        Announcement a = Announcement.builder().id(1L).title("t").content("c").adminId(1).adminName("admin").build();
        when(announcementRepository.findTop50ByOrderByCreatedAtDesc()).thenReturn(List.of(a));

        ResponseEntity<List<AnnouncementDto>> resp = adminResource.getAnnouncements();

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals(1, resp.getBody().size());
        assertEquals("t", resp.getBody().get(0).getTitle());
    }

    // ── checkPrivilege — ADMIN target protection ──────────────────────────────

    @Test
    void suspend_adminTargetByNonPlatformAdmin_returns403() {
        User target = User.builder().userId(2).role("ADMIN").username("mgr").build();
        when(authService.getUserById(2)).thenReturn(target);

        ResponseEntity<User> resp = adminResource.suspend(2, 1, "ADMIN",
                mock(HttpServletRequest.class));

        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
    }

    @Test
    void checkPrivilege_getUserByIdThrows_returnsNotFound() {
        when(authService.getUserById(99)).thenThrow(new RuntimeException("not found"));

        ResponseEntity<User> resp = adminResource.suspend(99, 1, "PLATFORM_ADMIN",
                mock(HttpServletRequest.class));

        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
    }
}
