package com.connecthub.notification.resource;

import com.connecthub.notification.entity.UserEmailPreference;
import com.connecthub.notification.repository.DeviceTokenRepository;
import com.connecthub.notification.repository.UserEmailPreferenceRepository;
import com.connecthub.notification.service.NotifService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotifResourceEmailPreferenceTest {

    @Mock private NotifService service;
    @Mock private DeviceTokenRepository deviceTokenRepo;
    @Mock private UserEmailPreferenceRepository emailPrefRepo;

    private NotifResource resource;

    @BeforeEach
    void setUp() {
        resource = new NotifResource(service, deviceTokenRepo, emailPrefRepo);
    }

    // ── getEmailPreference ────────────────────────────────────────────────────

    @Test
    void getEmailPreference_existingUser_returnsStoredValue() {
        UserEmailPreference pref = UserEmailPreference.builder()
                .userId(1).email("a@b.com").emailNotificationsEnabled(false).build();
        when(emailPrefRepo.findById(1)).thenReturn(Optional.of(pref));

        ResponseEntity<Map<String, Object>> resp = resource.getEmailPreference(1);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertFalse((Boolean) resp.getBody().get("emailNotificationsEnabled"));
    }

    @Test
    void getEmailPreference_noExistingRow_defaultsToTrue() {
        when(emailPrefRepo.findById(99)).thenReturn(Optional.empty());

        ResponseEntity<Map<String, Object>> resp = resource.getEmailPreference(99);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertTrue((Boolean) resp.getBody().get("emailNotificationsEnabled"));
    }

    // ── saveEmailPreference ───────────────────────────────────────────────────

    @Test
    void saveEmailPreference_existingRow_updatesAndSaves() {
        UserEmailPreference existing = UserEmailPreference.builder()
                .userId(1).email("old@b.com").emailNotificationsEnabled(true).build();
        when(emailPrefRepo.findById(1)).thenReturn(Optional.of(existing));

        ResponseEntity<Void> resp = resource.saveEmailPreference(
                1, "new@b.com", Map.of("emailNotificationsEnabled", false));

        assertEquals(HttpStatus.NO_CONTENT, resp.getStatusCode());
        assertFalse(existing.isEmailNotificationsEnabled());
        verify(emailPrefRepo).save(existing);
    }

    @Test
    void saveEmailPreference_newRow_createsAndSaves() {
        when(emailPrefRepo.findById(2)).thenReturn(Optional.empty());

        ResponseEntity<Void> resp = resource.saveEmailPreference(
                2, "new@b.com", Map.of("emailNotificationsEnabled", true));

        assertEquals(HttpStatus.NO_CONTENT, resp.getStatusCode());
        verify(emailPrefRepo).save(argThat(p -> "new@b.com".equals(p.getEmail()) && p.getUserId() == 2));
    }

    @Test
    void saveEmailPreference_backfillsEmailIfBlank() {
        UserEmailPreference existing = UserEmailPreference.builder()
                .userId(3).email("").emailNotificationsEnabled(true).build();
        when(emailPrefRepo.findById(3)).thenReturn(Optional.of(existing));

        resource.saveEmailPreference(3, "filled@b.com", Map.of("emailNotificationsEnabled", true));

        assertEquals("filled@b.com", existing.getEmail());
    }

    // ── device token ──────────────────────────────────────────────────────────

    @Test
    void registerDeviceToken_savesToken() {
        ResponseEntity<Void> resp = resource.registerDeviceToken(
                5, Map.of("fcmToken", "token123", "platform", "ANDROID"));

        assertEquals(HttpStatus.CREATED, resp.getStatusCode());
        verify(deviceTokenRepo).save(argThat(t ->
                "token123".equals(t.getFcmToken()) &&
                "ANDROID".equals(t.getPlatform()) &&
                t.getUserId() == 5));
    }

    @Test
    void registerDeviceToken_defaultPlatform_usesUnknown() {
        ResponseEntity<Void> resp = resource.registerDeviceToken(
                5, Map.of("fcmToken", "tok"));

        assertEquals(HttpStatus.CREATED, resp.getStatusCode());
        verify(deviceTokenRepo).save(argThat(t -> "UNKNOWN".equals(t.getPlatform())));
    }

    @Test
    void removeDeviceToken_deletesToken() {
        ResponseEntity<Void> resp = resource.removeDeviceToken("tok_to_remove");

        assertEquals(HttpStatus.NO_CONTENT, resp.getStatusCode());
        verify(deviceTokenRepo).deleteByFcmToken("tok_to_remove");
    }
}
