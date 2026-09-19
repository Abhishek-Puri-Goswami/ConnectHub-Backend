package com.connecthub.auth.service;

import com.connecthub.auth.exception.TooManyRequestsException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Brute-force protection for everything that checks a password.
 *
 * <ul>
 *   <li><b>Per account</b>: {@value #ACCOUNT_MAX_FAILURES} wrong passwords lock that account's password login
 *       for {@value #LOCK_SECONDS}s. The key is the user id when the account exists and the typed identifier
 *       otherwise, so an unknown name is throttled exactly like a real one (no account enumeration). A correct
 *       password clears the counter. Email-OTP login and password reset are separate flows and stay available,
 *       so a locked-out owner is never stuck.</li>
 *   <li><b>Per client IP</b>: {@value #IP_MAX_FAILURES} failures per {@value #WINDOW_SECONDS}s from one address
 *       block further attempts from it (password spraying across many accounts).</li>
 * </ul>
 * State lives in Redis with TTLs, so locks expire on their own and survive a service restart.
 * Trade-off (deliberate, standard for this design): someone who knows a username can lock that account's password
 * login for {@value #LOCK_SECONDS}s.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LoginAttemptService {

    static final int ACCOUNT_MAX_FAILURES = 5;
    static final int IP_MAX_FAILURES = 30;
    static final int WINDOW_SECONDS = 15 * 60;
    static final int LOCK_SECONDS = 15 * 60;

    private static final String ACCOUNT_PREFIX = "login:fail:acct:";
    private static final String IP_PREFIX = "login:fail:ip:";

    private final StringRedisTemplate redis;

    /** Throws 429 if this account is currently locked. {@code accountKey} identifies the account (see class doc). */
    public void assertAccountAllowed(String accountKey) {
        check(ACCOUNT_PREFIX + accountKey, ACCOUNT_MAX_FAILURES, "Too many failed attempts");
    }

    public void recordAccountFailure(String accountKey) {
        String key = ACCOUNT_PREFIX + accountKey;
        Long n = redis.opsForValue().increment(key);
        if (n == null) return;
        if (n == 1) redis.expire(key, WINDOW_SECONDS, TimeUnit.SECONDS);
        if (n == ACCOUNT_MAX_FAILURES) {
            // the lock starts now and lasts a full LOCK_SECONDS, whatever was left of the counting window
            redis.expire(key, LOCK_SECONDS, TimeUnit.SECONDS);
            log.warn("Password attempts locked for {} for {}s", accountKey.replaceAll("^(.).*", "$1***"), LOCK_SECONDS);
        }
    }

    public void clearAccount(String accountKey) {
        redis.delete(ACCOUNT_PREFIX + accountKey);
    }

    public void assertIpAllowed(String ip) {
        if (ip == null) return;
        check(IP_PREFIX + ip, IP_MAX_FAILURES, "Too many failed attempts from your network");
    }

    public void recordIpFailure(String ip) {
        if (ip == null) return;
        String key = IP_PREFIX + ip;
        Long n = redis.opsForValue().increment(key);
        if (n != null && n == 1) redis.expire(key, WINDOW_SECONDS, TimeUnit.SECONDS);
    }

    /** Identifier used for accounts that do not exist: normalised so "Bob@X.com" and "bob@x.com " share a counter. */
    public static String unknownAccountKey(String identifier) {
        return "n:" + (identifier == null ? "" : identifier.trim().toLowerCase());
    }

    public static String userAccountKey(int userId) {
        return "u:" + userId;
    }

    private void check(String key, int max, String message) {
        String raw = redis.opsForValue().get(key);
        if (raw == null) return;
        long count;
        try {
            count = Long.parseLong(raw);
        } catch (NumberFormatException e) {
            return;
        }
        if (count >= max) {
            Long ttl = redis.getExpire(key, TimeUnit.SECONDS);
            long retry = ttl != null && ttl > 0 ? ttl : LOCK_SECONDS;
            throw new TooManyRequestsException(message + ". Try again in " + ((retry + 59) / 60) + " minute(s).", retry);
        }
    }
}
