package com.connecthub.media.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MediaTierLimitsTest {

    @Test
    void normalizeTier_null_returnsFree() {
        assertThat(MediaTierLimits.normalizeTier(null)).isEqualTo("FREE");
    }

    @Test
    void normalizeTier_blank_returnsFree() {
        assertThat(MediaTierLimits.normalizeTier("   ")).isEqualTo("FREE");
    }

    @Test
    void normalizeTier_lowercasePro_returnsUppercase() {
        assertThat(MediaTierLimits.normalizeTier("pro")).isEqualTo("PRO");
    }

    @Test
    void normalizeTier_withWhitespace_trims() {
        assertThat(MediaTierLimits.normalizeTier(" PRO ")).isEqualTo("PRO");
    }

    @Test
    void storageCapKb_free_returns100MB() {
        assertThat(MediaTierLimits.storageCapKb("FREE")).isEqualTo(100L * 1024L);
    }

    @Test
    void storageCapKb_pro_returns10GB() {
        assertThat(MediaTierLimits.storageCapKb("PRO")).isEqualTo(10L * 1024L * 1024L);
    }

    @Test
    void storageCapKb_business_returns10GB() {
        assertThat(MediaTierLimits.storageCapKb("BUSINESS")).isEqualTo(10L * 1024L * 1024L);
    }

    @Test
    void storageCapKb_unknown_returnsFreeLimit() {
        assertThat(MediaTierLimits.storageCapKb("ENTERPRISE")).isEqualTo(100L * 1024L);
    }

    @Test
    void uploadsPerMinute_free_returns5() {
        assertThat(MediaTierLimits.uploadsPerMinute("FREE")).isEqualTo(5);
    }

    @Test
    void uploadsPerMinute_pro_returns30() {
        assertThat(MediaTierLimits.uploadsPerMinute("PRO")).isEqualTo(30);
    }

    @Test
    void uploadsPerMinute_business_returns30() {
        assertThat(MediaTierLimits.uploadsPerMinute("BUSINESS")).isEqualTo(30);
    }

    @Test
    void uploadsPerMinute_null_returnsFreeLimits() {
        assertThat(MediaTierLimits.uploadsPerMinute(null)).isEqualTo(5);
    }

    // ─── Regression coverage for the PRO/BUSINESS-only bug ───────────────────
    // The JWT/gateway actually emits FREE/PREMIUM/PLATINUM (see auth-service's
    // JwtUtil and AuthServiceImpl.resolvedTier), not PRO/BUSINESS. A paying
    // PREMIUM/PLATINUM user was silently falling through to FREE limits because
    // nothing above ever exercised these exact strings.

    @Test
    void storageCapKb_premium_returns10GB() {
        assertThat(MediaTierLimits.storageCapKb("PREMIUM")).isEqualTo(10L * 1024L * 1024L);
    }

    @Test
    void storageCapKb_platinum_returns10GB() {
        assertThat(MediaTierLimits.storageCapKb("PLATINUM")).isEqualTo(10L * 1024L * 1024L);
    }

    @Test
    void uploadsPerMinute_premium_returns30() {
        assertThat(MediaTierLimits.uploadsPerMinute("PREMIUM")).isEqualTo(30);
    }

    @Test
    void uploadsPerMinute_platinum_returns30() {
        assertThat(MediaTierLimits.uploadsPerMinute("PLATINUM")).isEqualTo(30);
    }

    @Test
    void maxFileSizeKb_free_returns10MB() {
        assertThat(MediaTierLimits.maxFileSizeKb("FREE")).isEqualTo(10L * 1024L);
    }

    @Test
    void maxFileSizeKb_premium_returns250MB() {
        assertThat(MediaTierLimits.maxFileSizeKb("PREMIUM")).isEqualTo(250L * 1024L);
    }

    @Test
    void maxFileSizeKb_platinum_returns250MB() {
        assertThat(MediaTierLimits.maxFileSizeKb("PLATINUM")).isEqualTo(250L * 1024L);
    }
}
