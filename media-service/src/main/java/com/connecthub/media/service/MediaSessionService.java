package com.connecthub.media.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Optional;

/**
 * Short-lived, signed "media session" tokens carried in an HttpOnly cookie.
 *
 * Why: files are referenced by permanent URLs stored inside messages and profiles, and an
 * {@code <img>}/{@code <video>} tag cannot send an Authorization header. The frontend therefore
 * asks for a media session (authenticated normally) and the browser attaches the cookie to file
 * requests. The token carries only the user id and its lifetime, is bound to this purpose by a
 * domain-separated HMAC (a login JWT can't be replayed as one, or vice versa), and is rejected
 * once the user is suspended or has invalidated their sessions (password reset / revoke-all).
 *
 * Format: {@code v1.<userId>.<issuedAtSec>.<expiresAtSec>.<base64url hmac>}.
 */
@Service
public class MediaSessionService {

    public static final long LIFETIME_SECONDS = 30 * 60;
    private static final String DOMAIN = "connecthub-media-session|";

    private final byte[] secret;
    private final StringRedisTemplate redis;

    public MediaSessionService(@Value("${media.signing-secret}") String secret, StringRedisTemplate redis) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.redis = redis;
    }

    public String issue(int userId) {
        return issue(userId, System.currentTimeMillis() / 1000);
    }

    String issue(int userId, long nowSec) {
        String body = "v1." + userId + "." + nowSec + "." + (nowSec + LIFETIME_SECONDS);
        return body + "." + sign(body);
    }

    /** The user the token was issued to, if it is authentic, unexpired, and not revoked. */
    public Optional<Integer> verify(String token) {
        return verify(token, System.currentTimeMillis() / 1000);
    }

    Optional<Integer> verify(String token, long nowSec) {
        if (token == null) return Optional.empty();
        String[] p = token.split("\\.");
        if (p.length != 5 || !"v1".equals(p[0])) return Optional.empty();
        String body = p[0] + "." + p[1] + "." + p[2] + "." + p[3];
        if (!MessageDigest.isEqual(sign(body).getBytes(StandardCharsets.UTF_8), p[4].getBytes(StandardCharsets.UTF_8)))
            return Optional.empty();
        try {
            int userId = Integer.parseInt(p[1]);
            long iat = Long.parseLong(p[2]);
            long exp = Long.parseLong(p[3]);
            if (nowSec >= exp) return Optional.empty();
            if (Boolean.TRUE.equals(redis.hasKey("user:suspended:" + userId))) return Optional.empty();
            if (isInvalidated(iat, redis.opsForValue().get("user:invalidated:" + userId))) return Optional.empty();
            return Optional.of(userId);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /** Same rule as the gateway: dead only if issued before the user's last invalidation. */
    static boolean isInvalidated(long iatSeconds, String storedMillis) {
        if (storedMillis == null || storedMillis.isBlank()) return false;
        try {
            return iatSeconds < Long.parseLong(storedMillis.trim()) / 1000;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private String sign(String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal((DOMAIN + body).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC unavailable", e);
        }
    }
}
