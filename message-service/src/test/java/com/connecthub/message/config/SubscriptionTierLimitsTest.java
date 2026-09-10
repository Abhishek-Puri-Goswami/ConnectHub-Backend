package com.connecthub.message.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SubscriptionTierLimitsTest {

    @Test
    void normalizeTier_null_returnsFree() {
        assertThat(SubscriptionTierLimits.normalizeTier(null)).isEqualTo("FREE");
    }

    @Test
    void normalizeTier_blank_returnsFree() {
        assertThat(SubscriptionTierLimits.normalizeTier("   ")).isEqualTo("FREE");
    }

    // Message rate limiting is a uniform anti-abuse limit, not a billing
    // differentiator — every tier must get the same cap. These guard against
    // ever reintroducing a tier-based split here (that belongs in
    // room-service/media-service instead).

    @Test
    void messagesPerMinute_isSameForEveryTier() {
        int free = SubscriptionTierLimits.messagesPerMinute("FREE");
        int premium = SubscriptionTierLimits.messagesPerMinute("PREMIUM");
        int platinum = SubscriptionTierLimits.messagesPerMinute("PLATINUM");
        int unknown = SubscriptionTierLimits.messagesPerMinute(null);

        assertThat(premium).isEqualTo(free);
        assertThat(platinum).isEqualTo(free);
        assertThat(unknown).isEqualTo(free);
        assertThat(free).isEqualTo(SubscriptionTierLimits.MESSAGES_PER_MINUTE);
    }
}
