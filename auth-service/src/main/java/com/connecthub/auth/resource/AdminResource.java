package com.connecthub.auth.resource;

import com.connecthub.auth.entity.AuditLog;
import com.connecthub.auth.entity.User;
import com.connecthub.auth.service.AuditService;
import com.connecthub.auth.service.AuthService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth/admin")
@RequiredArgsConstructor
@Tag(name = "Platform Admin", description = "User management, audit logs")
public class AdminResource {

    private final AuthService authService;
    private final AuditService auditService;

    @PutMapping("/users/{userId}/suspend")
    public ResponseEntity<User> suspend(@PathVariable int userId, @RequestHeader("X-User-Id") int adminId, HttpServletRequest req) {
        User u = authService.suspendUser(userId);
        auditService.log(adminId, "USER_SUSPEND", "USER", String.valueOf(userId), "Suspended: " + u.getUsername(), req.getRemoteAddr());
        return ResponseEntity.ok(u);
    }

    @PutMapping("/users/{userId}/reactivate")
    public ResponseEntity<User> reactivate(@PathVariable int userId, @RequestHeader("X-User-Id") int adminId, HttpServletRequest req) {
        User u = authService.reactivateUser(userId);
        auditService.log(adminId, "USER_REACTIVATE", "USER", String.valueOf(userId), "Reactivated: " + u.getUsername(), req.getRemoteAddr());
        return ResponseEntity.ok(u);
    }

    @DeleteMapping("/users/{userId}")
    public ResponseEntity<Void> delete(@PathVariable int userId, @RequestHeader("X-User-Id") int adminId, HttpServletRequest req) {
        User u = authService.getUserById(userId);
        authService.deleteUser(userId);
        auditService.log(adminId, "USER_DELETE", "USER", String.valueOf(userId), "Deleted: " + u.getUsername(), req.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/audit")
    public ResponseEntity<Page<AuditLog>> getAuditLogs(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(auditService.getLogs(page, size));
    }

    @GetMapping("/users")
    public ResponseEntity<java.util.List<User>> getAllUsers() {
        return ResponseEntity.ok(authService.getAllUsers());
    }
}
