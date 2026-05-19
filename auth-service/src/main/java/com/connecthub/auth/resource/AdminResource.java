package com.connecthub.auth.resource;

import com.connecthub.auth.entity.AnalyticsSnapshot;
import com.connecthub.auth.entity.AuditLog;
import com.connecthub.auth.entity.User;
import com.connecthub.auth.service.AnalyticsService;
import com.connecthub.auth.service.AuditService;
import com.connecthub.auth.service.AuthService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/v1/auth/admin")
@RequiredArgsConstructor
@Tag(name = "Platform Admin", description = "User management, audit logs")
public class AdminResource {

    private final AuthService authService;
    private final AuditService auditService;
    private final AnalyticsService analyticsService;
    private final StringRedisTemplate redis;

    private static final String SUSPENDED_CHANNEL = "chat:suspended";

    /**
     * Privilege check applied before every mutating admin action.
     * Rules (matching real-world role hierarchy):
     *   - PLATFORM_ADMIN targets are always protected — nobody can touch them.
     *   - ADMIN targets require the caller to be PLATFORM_ADMIN.
     *   - USER / GUEST targets are accessible to any admin.
     * Returns a 403 ResponseEntity when access is denied, null when allowed.
     */
    private ResponseEntity<Void> checkPrivilege(String requesterRole, int targetUserId) {
        User target;
        try { target = authService.getUserById(targetUserId); }
        catch (Exception e) { return ResponseEntity.notFound().build(); }
        String targetRole = (target.getRole() != null ? target.getRole() : "USER").toUpperCase();
        if ("PLATFORM_ADMIN".equals(targetRole)) return ResponseEntity.status(403).build();
        if ("ADMIN".equals(targetRole) && !"PLATFORM_ADMIN".equalsIgnoreCase(requesterRole))
            return ResponseEntity.status(403).build();
        return null;
    }

    @PutMapping("/users/{userId}/suspend")
    public ResponseEntity<User> suspend(@PathVariable int userId,
            @RequestHeader("X-User-Id") int adminId,
            @RequestHeader(value = "X-User-Role", defaultValue = "") String requesterRole,
            HttpServletRequest req) {
        ResponseEntity<Void> denied = checkPrivilege(requesterRole, userId);
        if (denied != null) return ResponseEntity.status(denied.getStatusCode()).build();
        User u = authService.suspendUser(userId);
        redis.opsForValue().set("user:suspended:" + userId, "1", 30, TimeUnit.DAYS);
        redis.opsForValue().set("user:invalidated:" + userId, "1", 30, TimeUnit.DAYS);
        redis.convertAndSend(SUSPENDED_CHANNEL, String.valueOf(userId));
        auditService.log(adminId, "USER_SUSPEND", "USER", String.valueOf(userId), "Suspended: " + u.getUsername(), req.getRemoteAddr());
        return ResponseEntity.ok(u);
    }

    @PutMapping("/users/{userId}/reactivate")
    public ResponseEntity<User> reactivate(@PathVariable int userId,
            @RequestHeader("X-User-Id") int adminId,
            @RequestHeader(value = "X-User-Role", defaultValue = "") String requesterRole,
            HttpServletRequest req) {
        ResponseEntity<Void> denied = checkPrivilege(requesterRole, userId);
        if (denied != null) return ResponseEntity.status(denied.getStatusCode()).build();
        User u = authService.reactivateUser(userId);
        redis.delete("user:suspended:" + userId);
        redis.delete("user:invalidated:" + userId);
        auditService.log(adminId, "USER_REACTIVATE", "USER", String.valueOf(userId), "Reactivated: " + u.getUsername(), req.getRemoteAddr());
        return ResponseEntity.ok(u);
    }

    @DeleteMapping("/users/{userId}")
    public ResponseEntity<Void> delete(@PathVariable int userId,
            @RequestHeader("X-User-Id") int adminId,
            @RequestHeader(value = "X-User-Role", defaultValue = "") String requesterRole,
            HttpServletRequest req) {
        ResponseEntity<Void> denied = checkPrivilege(requesterRole, userId);
        if (denied != null) return denied;
        User u = authService.getUserById(userId);
        authService.deleteUser(userId);
        auditService.log(adminId, "USER_DELETE", "USER", String.valueOf(userId), "Deleted: " + u.getUsername(), req.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/users/{userId}/role")
    public ResponseEntity<User> changeRole(@PathVariable int userId, @RequestBody java.util.Map<String, String> body,
            @RequestHeader("X-User-Id") int adminId,
            @RequestHeader(value = "X-User-Role", defaultValue = "") String requesterRole,
            HttpServletRequest req) {
        if (!"PLATFORM_ADMIN".equalsIgnoreCase(requesterRole)) {
            return ResponseEntity.status(403).build();
        }
        String role = body.get("role");
        User u = authService.changeRole(userId, role);
        auditService.log(adminId, "USER_ROLE_CHANGE", "USER", String.valueOf(userId), "Role changed to " + role + " for: " + u.getUsername(), req.getRemoteAddr());
        return ResponseEntity.ok(u);
    }

    @GetMapping("/audit")
    public ResponseEntity<Page<AuditLog>> getAuditLogs(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(auditService.getLogs(page, size));
    }

    @GetMapping("/users")
    public ResponseEntity<java.util.List<User>> getAllUsers() {
        return ResponseEntity.ok(authService.getAllUsers());
    }

    /** Returns the last 96 analytics snapshots (24h at 15-min intervals) for the admin dashboard charts. */
    @GetMapping("/analytics")
    public ResponseEntity<java.util.List<AnalyticsSnapshot>> getAnalytics() {
        return ResponseEntity.ok(analyticsService.getRecentSnapshots());
    }
}
