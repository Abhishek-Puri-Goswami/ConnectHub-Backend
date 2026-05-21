package com.connecthub.message.config;

/**
 * AppConstants — Message-Service String Constants
 *
 * Central registry for all literal string values used in this service:
 * Kafka topic names, HTTP header names, and delivery status codes.
 *
 * WHY A CONSTANTS CLASS?
 *   Using raw string literals scattered across multiple classes risks:
 *   - Silent typos (e.g. "chat.messages.oubound") that compile fine but cause
 *     runtime misrouting where messages are silently dropped.
 *   - Difficulty refactoring — a topic rename requires a grep across every file.
 *   - No IDE auto-complete or "Find Usages" support on plain strings.
 *
 *   Centralising them here means:
 *   - One source of truth for every topic and header name.
 *   - IDE refactoring updates all callers automatically.
 *   - @KafkaListener annotations can reference the constant via SpEL
 *     or the raw literal can be replaced with the constant name in code.
 *
 * KAFKA TOPICS:
 *   Inbound topics are consumed by this service (message persistence path).
 *   Outbound / rejected topics are published to by this service.
 *
 * HTTP HEADERS:
 *   Injected by the API Gateway's JwtAuthenticationFilter before forwarding
 *   requests to this service. Controllers read them to determine the caller's
 *   identity and subscription tier without re-parsing the JWT.
 *
 * DELIVERY STATUS:
 *   String codes written to the Message.deliveryStatus column.
 *   Kept as constants so any future status addition is a single-file change.
 */
public final class AppConstants {

    private AppConstants() {
        // Utility class — do not instantiate
    }

    // ── Kafka Topics ──────────────────────────────────────────────────────────

    /** Incoming messages from websocket-service awaiting persistence. */
    public static final String TOPIC_MESSAGES_INBOUND  = "chat.messages.inbound";

    /** Dead-letter queue for messages that could not be processed after all retries. */
    public static final String TOPIC_MESSAGES_INBOUND_DLQ = "chat.messages.inbound.dlq";

    /** Persisted messages broadcast to downstream consumers (websocket fanout, search index). */
    public static final String TOPIC_MESSAGES_OUTBOUND = "chat.messages.outbound";

    /** Messages rejected due to rate limits or validation failures (returned to sender). */
    public static final String TOPIC_MESSAGES_REJECTED = "chat.messages.rejected";

    // ── HTTP Headers ──────────────────────────────────────────────────────────

    /** Gateway-injected header carrying the authenticated user's numeric ID. */
    public static final String HEADER_USER_ID          = "X-User-Id";

    /** Gateway-injected header carrying the user's subscription tier (FREE / PREMIUM / PLATINUM). */
    public static final String HEADER_SUBSCRIPTION_TIER = "X-Subscription-Tier";

    // ── Delivery Status Codes ─────────────────────────────────────────────────

    /** Message saved to the database; not yet delivered to any recipient. */
    public static final String STATUS_SENT      = "SENT";

    /** Message delivered to at least one recipient's device/session. */
    public static final String STATUS_DELIVERED = "DELIVERED";

    /** Message has been read by all intended recipients. */
    public static final String STATUS_READ      = "READ";
}
