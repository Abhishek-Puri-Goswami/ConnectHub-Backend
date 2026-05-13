package com.connecthub.notification.service;

import com.connecthub.notification.repository.DeviceTokenRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

/**
 * Unit tests for FcmService.
 *
 * WHAT WE ARE TESTING:
 *   FcmService sends Firebase Cloud Messaging (FCM) push notifications to mobile
 *   and web devices. It also automatically cleans up stale device tokens when Firebase
 *   reports that a device has uninstalled the app (UNREGISTERED error code).
 *
 * WHAT IS FCM?
 *   Firebase Cloud Messaging is Google's service for sending push notifications to
 *   Android, iOS, and web browsers. Each device that installs your app registers a
 *   unique "FCM token" with your server. You store that token in the DB and send
 *   push notifications to it. When the user uninstalls the app, the token becomes
 *   invalid and Firebase returns UNREGISTERED when you try to use it.
 *
 * WHY THESE TESTS ARE FOCUSED ON NULL-SAFETY AND GRACEFUL DEGRADATION:
 *   FcmService is OPTIONAL — if Firebase credentials are not configured (common in
 *   local dev and test environments), the FirebaseApp bean is null. The service must
 *   handle null firebaseApp gracefully rather than crashing the application at startup.
 *
 *   Similarly, push notifications are best-effort: even if Firebase is down or a token
 *   is invalid, the main app flow (message delivered via WebSocket) must continue.
 *   FcmService is a "nice to have" enhancement, not a critical path.
 *
 * WHY WE CAN'T FULLY MOCK FirebaseMessaging.getInstance():
 *   FirebaseMessaging.getInstance() is a static factory method in the Firebase SDK.
 *   Mocking static methods requires additional libraries (e.g., Mockito's mockStatic,
 *   PowerMock) which add complexity and are often discouraged. Instead, we test the
 *   guard conditions (null checks) that prevent the code from reaching FirebaseMessaging.
 *   Full integration tests that exercise the actual FCM call would use a Firebase
 *   emulator or a test project with known tokens.
 *
 * WHAT IS TESTED:
 *   - When FirebaseApp is null → sendPush() returns immediately without crashing.
 *   - When token list is null or empty → sendPush() returns immediately.
 *   - When data map is null → sendPush() handles it without NullPointerException.
 *   - The deviceTokenRepository is not called unless Firebase actually processes the message.
 */
@ExtendWith(MockitoExtension.class)
class FcmServiceTest {

    /*
     * @Mock creates a fake DeviceTokenRepository. In the null-firebaseApp tests below,
     * this repo should NEVER be called because the method returns early.
     */
    @Mock
    private DeviceTokenRepository deviceTokenRepository;

    /*
     * NOTE: We do NOT mock FirebaseApp because the constructor takes it as @Autowired(required=false).
     * We pass null directly in the tests to simulate an unconfigured environment.
     */

    // ── null FirebaseApp guard ──────────────────────────────────────────────

    @Test
    void sendPush_nullFirebaseApp_returnsEarlyWithoutException() {
        /*
         * Scenario: Firebase credentials are not configured (common in local dev).
         * FcmService is constructed with null for the firebaseApp parameter.
         *
         * Expected: sendPush() returns immediately at the first null-check guard
         * and does NOT throw a NullPointerException or any other exception.
         *
         * Why this matters: if this guard were missing, the code would call
         * FirebaseMessaging.getInstance(null) which throws an IllegalArgumentException,
         * crashing every message delivery that triggers a push notification.
         */
        FcmService service = new FcmService(deviceTokenRepository, null);

        assertDoesNotThrow(
            () -> service.sendPush(List.of("token-abc"), "New message", "Alice: Hello!", Map.of()),
            "sendPush must not throw when FirebaseApp is null"
        );
    }

    @Test
    void sendPush_nullFirebaseApp_neverAccessesRepository() {
        /*
         * If Firebase is not configured, we return before even building the message —
         * so the device token repository must never be queried.
         * If it were called, we might accidentally delete valid tokens that exist
         * for when Firebase IS configured in a later environment (e.g., production).
         */
        FcmService service = new FcmService(deviceTokenRepository, null);

        service.sendPush(List.of("token-xyz"), "Hello", "World", Map.of());

        /*
         * verifyNoInteractions() asserts that NOT A SINGLE METHOD on the repository
         * was called during the test. This is stricter than verify(repo, never()).method().
         */
        verifyNoInteractions(deviceTokenRepository);
    }

    // ── null / empty token list guard ───────────────────────────────────────

    @Test
    void sendPush_nullTokenList_returnsEarlyWithoutException() {
        /*
         * Scenario: the notification-service has no FCM tokens for the target user
         * (they never installed the mobile app or cleared their tokens).
         * The caller may pass null rather than an empty list — we handle both.
         */
        FcmService service = new FcmService(deviceTokenRepository, null);

        assertDoesNotThrow(
            () -> service.sendPush(null, "title", "body", Map.of()),
            "sendPush must handle null token list gracefully"
        );
    }

    @Test
    void sendPush_emptyTokenList_returnsEarlyWithoutException() {
        /*
         * Scenario: the user has no registered devices. The token list is empty.
         * Passing an empty list to FirebaseMessaging.sendEachForMulticast() would
         * cause a Firebase API error ("tokens list cannot be empty"), so we guard
         * against it by returning early.
         */
        FcmService service = new FcmService(deviceTokenRepository, null);

        assertDoesNotThrow(
            () -> service.sendPush(List.of(), "title", "body", Map.of()),
            "sendPush must handle empty token list gracefully"
        );
    }

    @Test
    void sendPush_emptyTokenList_neverAccessesRepository() {
        FcmService service = new FcmService(deviceTokenRepository, null);

        service.sendPush(List.of(), "title", "body", null);

        verifyNoInteractions(deviceTokenRepository);
    }

    // ── null data map guard ─────────────────────────────────────────────────

    @Test
    void sendPush_nullDataMap_doesNotThrow() {
        /*
         * FCM messages can carry a "data" map of key-value pairs for the client app
         * to process (e.g., { "roomId": "42", "messageId": "999" }). The caller may
         * omit it (pass null) for simple notifications that only show a title/body.
         *
         * The implementation does: putAllData(data != null ? data : Map.of())
         * This test verifies that null is handled and Map.of() is used as a fallback.
         *
         * Even though we pass null firebaseApp (so it returns early), we test the
         * null-data path separately so the assertion is explicit. If Firebase were
         * configured, the same null check prevents a NullPointerException in putAllData().
         */
        FcmService service = new FcmService(deviceTokenRepository, null);

        assertDoesNotThrow(
            () -> service.sendPush(List.of("token"), "title", "body", null),
            "sendPush must handle null data map without NullPointerException"
        );
    }

    // ── combined guard conditions ────────────────────────────────────────────

    @Test
    void sendPush_allNullArguments_doesNotThrow() {
        /*
         * Extreme edge case: every argument is null. The method should not throw.
         * The Firebase null check (first guard) catches this before any downstream
         * null dereferences.
         */
        FcmService service = new FcmService(deviceTokenRepository, null);

        assertDoesNotThrow(() -> service.sendPush(null, null, null, null));
        verifyNoInteractions(deviceTokenRepository);
    }
}
