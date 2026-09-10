package com.connecthub.message.config;

/**
 * Message send-rate limiting.
 *
 * This is a uniform anti-abuse/anti-spam limit, not a billing differentiator —
 * every tier gets the same cap. Gating core conversational throughput behind a
 * paywall makes the free tier feel broken rather than "limited," which is the
 * wrong trade for a chat product. Billing differentiators (group room count,
 * members per room, storage) live in room-service and media-service instead.
 */
public final class SubscriptionTierLimits {

    /** Same for every tier — generous enough for real conversation, tight enough to block spam bots. */
    public static final int MESSAGES_PER_MINUTE = 60;

    private SubscriptionTierLimits() {}

    public static String normalizeTier(String headerOrTokenTier) {
        if (headerOrTokenTier == null || headerOrTokenTier.isBlank()) return "FREE";
        return headerOrTokenTier.trim().toUpperCase();
    }

    /**
     * @param tier normalized tier string — accepted for call-site/API compatibility,
     *             but ignored: the rate limit is intentionally the same for everyone.
     */
    public static int messagesPerMinute(String tier) {
        return MESSAGES_PER_MINUTE;
    }
}
