package com.connecthub.auth.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for EmailEventPublisher.
 *
 * WHAT WE ARE TESTING:
 *   EmailEventPublisher decouples auth-service from the email/SMS infrastructure
 *   by publishing JSON events to the Redis "email:send" pub/sub channel instead
 *   of calling SMTP or Twilio directly. The notification-service subscribes to
 *   that channel and does the actual sending.
 *
 * HOW THESE TESTS WORK:
 *   We use Mockito to replace StringRedisTemplate with a fake (mock) object.
 *   Instead of connecting to a real Redis server, the mock just records what
 *   methods were called and with what arguments. We then inspect those recorded
 *   calls to verify the correct JSON was published to the correct channel.
 *
 * KEY CONCEPT — ArgumentCaptor:
 *   An ArgumentCaptor is a Mockito helper that captures the actual argument
 *   passed to a mocked method. Instead of just saying "verify this method was
 *   called", we can say "capture what string was passed" and then assert on it.
 *   This lets us check the exact JSON payload without comparing entire strings.
 *
 * WHY THIS MATTERS:
 *   If EmailEventPublisher publishes to the wrong channel, or builds a JSON
 *   payload with the wrong fields, the notification-service will silently fail
 *   to send the email/SMS. These tests catch such mistakes early.
 */
@ExtendWith(MockitoExtension.class)
class EmailEventPublisherTest {

    /*
     * @Mock creates a fake StringRedisTemplate that does nothing by default.
     * We use it to verify that convertAndSend() is called with the right arguments.
     */
    @Mock
    private StringRedisTemplate redis;

    /*
     * @InjectMocks creates a real EmailEventPublisher instance and injects
     * the @Mock fields (redis) into it via the constructor or field injection.
     */
    @InjectMocks
    private EmailEventPublisher publisher;

    /*
     * The Redis channel that all email/SMS events are published to.
     * Must match the value in EmailEventPublisher and notification-service's
     * RedisListenerConfig exactly, otherwise events are never received.
     */
    private static final String EMAIL_CHANNEL = "email:send";

    // ── sendOtpEmail ────────────────────────────────────────────────────────

    @Test
    void sendOtpEmail_registration_publishesCorrectPayload() {
        /*
         * Arrange: nothing to set up — the mock redis will record the call.
         * Act: publish a registration OTP email event.
         * Assert: verify the payload contains all required fields.
         *
         * BEGINNER NOTE: ArgumentCaptor.forClass(String.class) creates a captor
         * that will intercept and store any String argument passed to convertAndSend.
         */
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);

        publisher.sendOtpEmail("user@example.com", "123456", "registration");

        /*
         * verify() asserts that convertAndSend was called exactly once on our
         * mock redis, with "email:send" as the first argument, and captures the
         * second argument (the JSON payload) into payloadCaptor.
         */
        verify(redis).convertAndSend(eq(EMAIL_CHANNEL), payloadCaptor.capture());

        String payload = payloadCaptor.getValue();
        assertTrue(payload.contains("user@example.com"), "Payload must include recipient email");
        assertTrue(payload.contains("123456"),           "Payload must include the OTP code");
        assertTrue(payload.contains("registration"),     "Payload must include the purpose field");
    }

    @Test
    void sendOtpEmail_passwordReset_publishesPasswordResetPurpose() {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);

        publisher.sendOtpEmail("reset@example.com", "999888", "password_reset");

        verify(redis).convertAndSend(eq(EMAIL_CHANNEL), captor.capture());

        String payload = captor.getValue();
        /*
         * The "purpose" field is used by EmailSender to select the email template.
         * "password_reset" maps to the "ConnectHub - Password Reset" template
         * which includes a security warning about unrecognised requests.
         */
        assertTrue(payload.contains("password_reset"), "Payload must include the password_reset purpose");
        assertTrue(payload.contains("reset@example.com"));
    }

    @Test
    void sendOtpEmail_loginPurpose_publishesLoginPurpose() {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);

        publisher.sendOtpEmail("login@example.com", "112233", "login");

        verify(redis).convertAndSend(eq(EMAIL_CHANNEL), captor.capture());

        assertTrue(captor.getValue().contains("login"));
    }

    @Test
    void sendOtpEmail_publishesExactlyOnce() {
        /*
         * Sanity check: one call to sendOtpEmail → one Redis publish.
         * If this ever fails, it means the method is double-publishing,
         * which would send users two emails for a single OTP request.
         */
        publisher.sendOtpEmail("a@b.com", "111222", "registration");

        verify(redis, times(1)).convertAndSend(anyString(), anyString());
    }

    // ── sendSmsOtp ──────────────────────────────────────────────────────────

    @Test
    void sendSmsOtp_publishesSmsChannelFlag() {
        /*
         * SMS and email events travel over the same Redis channel. The notification-service
         * uses the "channel":"sms" field to route the event to SmsSender instead of EmailSender.
         * If this flag is missing, the OTP is sent as an email to a phone number — useless.
         */
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);

        publisher.sendSmsOtp("+919876543210", "654321");

        verify(redis).convertAndSend(eq(EMAIL_CHANNEL), captor.capture());

        String payload = captor.getValue();
        assertTrue(payload.contains("+919876543210"),      "Payload must include the phone number");
        assertTrue(payload.contains("654321"),             "Payload must include the OTP");
        assertTrue(payload.contains("\"channel\":\"sms\""), "Payload must include the sms routing flag");
        assertTrue(payload.contains("sms_otp"),            "Payload must include the sms_otp purpose");
    }

    @Test
    void sendSmsOtp_publishesExactlyOnce() {
        publisher.sendSmsOtp("+919876543210", "000001");
        verify(redis, times(1)).convertAndSend(anyString(), anyString());
    }

    // ── sendWelcomeEmail ────────────────────────────────────────────────────

    @Test
    void sendWelcomeEmail_publishesUsernameAndRecipient() {
        /*
         * The welcome email is sent after the user's first successful login.
         * The notification-service uses the username to personalise the greeting.
         * If username is missing, the email reads "Hello, !" which looks like a bug.
         */
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);

        publisher.sendWelcomeEmail("new@example.com", "janedoe");

        verify(redis).convertAndSend(eq(EMAIL_CHANNEL), captor.capture());

        String payload = captor.getValue();
        assertTrue(payload.contains("new@example.com"), "Payload must include the recipient");
        assertTrue(payload.contains("janedoe"),         "Payload must include the username for personalisation");
        assertTrue(payload.contains("welcome"),         "Payload must include the welcome purpose");
    }

    @Test
    void sendWelcomeEmail_publishesExactlyOnce() {
        publisher.sendWelcomeEmail("x@y.com", "user1");
        verify(redis, times(1)).convertAndSend(anyString(), anyString());
    }

    @Test
    void sendWelcomeEmail_usernameWithSpecialCharacters_isIncluded() {
        /*
         * Usernames can contain underscores (e.g., "john_doe").
         * Verify the payload still contains the username correctly.
         */
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);

        publisher.sendWelcomeEmail("special@example.com", "john_doe_99");

        verify(redis).convertAndSend(eq(EMAIL_CHANNEL), captor.capture());

        assertTrue(captor.getValue().contains("john_doe_99"));
    }

    // ── channel routing ─────────────────────────────────────────────────────

    @Test
    void allMethods_publishToEmailSendChannel() {
        /*
         * This test verifies that all three methods publish to the SAME channel.
         * If any method uses a different channel name, the notification-service
         * won't receive the event because its subscriber only listens on "email:send".
         */
        publisher.sendOtpEmail("a@a.com", "1", "registration");
        publisher.sendSmsOtp("+91123", "2");
        publisher.sendWelcomeEmail("b@b.com", "user2");

        /*
         * verify(redis, times(3)) asserts that convertAndSend was called
         * exactly 3 times total (once per method call above).
         */
        verify(redis, times(3)).convertAndSend(eq(EMAIL_CHANNEL), anyString());
    }
}
