package com.connecthub.media.config;

public final class MediaTierLimits {

    /** Total media storage per user (KB). */
    public static final long FREE_STORAGE_KB = 100L * 1024L;
    public static final long PRO_STORAGE_KB = 10L * 1024L * 1024L;

    public static final int FREE_UPLOADS_PER_MINUTE = 5;
    public static final int PRO_UPLOADS_PER_MINUTE = 30;

    /** Max size of a single uploaded file (KB) — replaces the old flat 2MB/50MB constants. */
    public static final long FREE_MAX_FILE_SIZE_KB = 10L * 1024L;
    public static final long PRO_MAX_FILE_SIZE_KB = 250L * 1024L;

    private MediaTierLimits() {}

    public static String normalizeTier(String tier) {
        if (tier == null || tier.isBlank()) return "FREE";
        return tier.trim().toUpperCase();
    }

    private static boolean isPaid(String normalizedTier) {
        return "PRO".equals(normalizedTier) || "BUSINESS".equals(normalizedTier)
                || "PREMIUM".equals(normalizedTier) || "PLATINUM".equals(normalizedTier);
    }

    public static long storageCapKb(String tier) {
        return isPaid(normalizeTier(tier)) ? PRO_STORAGE_KB : FREE_STORAGE_KB;
    }

    public static int uploadsPerMinute(String tier) {
        return isPaid(normalizeTier(tier)) ? PRO_UPLOADS_PER_MINUTE : FREE_UPLOADS_PER_MINUTE;
    }

    public static long maxFileSizeKb(String tier) {
        return isPaid(normalizeTier(tier)) ? PRO_MAX_FILE_SIZE_KB : FREE_MAX_FILE_SIZE_KB;
    }
}
