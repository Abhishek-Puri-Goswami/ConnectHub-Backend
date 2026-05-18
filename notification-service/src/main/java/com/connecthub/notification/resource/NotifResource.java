package com.connecthub.notification.resource;
import com.connecthub.notification.entity.DeviceToken;
import com.connecthub.notification.entity.Notification;
import com.connecthub.notification.entity.UserEmailPreference;
import com.connecthub.notification.repository.DeviceTokenRepository;
import com.connecthub.notification.repository.UserEmailPreferenceRepository;
import com.connecthub.notification.service.NotifService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController @RequestMapping("/api/v1/notifications") @RequiredArgsConstructor
@Tag(name = "Notifications", description = "In-app notification management")
public class NotifResource {
    private final NotifService svc;
    private final DeviceTokenRepository deviceTokenRepo;
    private final UserEmailPreferenceRepository emailPrefRepo;

    @PostMapping public ResponseEntity<Notification> send(@RequestBody Notification n) { return ResponseEntity.status(HttpStatus.CREATED).body(svc.send(n)); }
    @GetMapping("/user/{uid}") public ResponseEntity<List<Notification>> get(@PathVariable int uid) { return ResponseEntity.ok(svc.getByRecipient(uid)); }
    @PutMapping("/{id}/read") public ResponseEntity<Void> read(@PathVariable int id) { svc.markRead(id); return ResponseEntity.noContent().build(); }
    @PutMapping("/user/{uid}/read-all") public ResponseEntity<Void> readAll(@PathVariable int uid) { svc.markAllRead(uid); return ResponseEntity.noContent().build(); }
    @GetMapping("/user/{uid}/unread-count") public ResponseEntity<Integer> unread(@PathVariable int uid) { return ResponseEntity.ok(svc.unreadCount(uid)); }
    @DeleteMapping("/{id}") public ResponseEntity<Void> del(@PathVariable int id) { svc.delete(id); return ResponseEntity.noContent().build(); }

    // ── Email notification preferences ────────────────────────────────────────

    @GetMapping("/email-preferences")
    @Operation(summary = "Get email notification preference for the authenticated user")
    public ResponseEntity<Map<String, Object>> getEmailPreference(
            @RequestHeader("X-User-Id") int userId) {
        boolean enabled = emailPrefRepo.findById(userId)
                .map(UserEmailPreference::isEmailNotificationsEnabled)
                .orElse(true); // default: enabled (no row means never changed)
        return ResponseEntity.ok(Map.of("emailNotificationsEnabled", enabled));
    }

    @PutMapping("/email-preferences")
    @Operation(summary = "Update email notification preference for the authenticated user")
    public ResponseEntity<Void> saveEmailPreference(
            @RequestHeader("X-User-Id") int userId,
            @RequestHeader(value = "X-User-Email", required = false) String userEmail,
            @RequestBody Map<String, Boolean> body) {
        boolean enabled = Boolean.TRUE.equals(body.get("emailNotificationsEnabled"));
        UserEmailPreference pref = emailPrefRepo.findById(userId)
                .orElse(UserEmailPreference.builder()
                        .userId(userId)
                        .email(userEmail != null ? userEmail : "")
                        .build());
        pref.setEmailNotificationsEnabled(enabled);
        pref.setUpdatedAt(LocalDateTime.now());
        // Backfill email if it was missing (e.g. row created without email header)
        if ((pref.getEmail() == null || pref.getEmail().isBlank()) && userEmail != null) {
            pref.setEmail(userEmail);
        }
        emailPrefRepo.save(pref);
        return ResponseEntity.noContent().build();
    }

    // ── FCM device tokens ─────────────────────────────────────────────────────

    @PostMapping("/device-token")
    public ResponseEntity<Void> registerDeviceToken(
            @RequestHeader("X-User-Id") int userId,
            @RequestBody Map<String, String> body) {
        DeviceToken token = DeviceToken.builder()
                .userId(userId)
                .fcmToken(body.get("fcmToken"))
                .platform(body.getOrDefault("platform", "UNKNOWN"))
                .build();
        deviceTokenRepo.save(token);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping("/device-token/{fcmToken}")
    public ResponseEntity<Void> removeDeviceToken(@PathVariable String fcmToken) {
        deviceTokenRepo.deleteByFcmToken(fcmToken);
        return ResponseEntity.noContent().build();
    }
}
