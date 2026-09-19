package com.connecthub.websocket.interceptor;

/**
 * Decides whether a token was issued before its user's last invalidation
 * (password reset, revoke-all-sessions, self-delete, suspension).
 *
 * The Redis key {@code user:invalidated:<id>} holds the epoch-millis moment of
 * invalidation. A token is dead only if it was issued before that moment, so a
 * fresh login after the event works while every older token is rejected.
 * JWT {@code iat} has one-second resolution, so a token issued in the same
 * second as the event is accepted (deliberate trade-off).
 * Duplicated in api-gateway, auth-service and websocket-service because the gateway (WebFlux)
 * does not depend on common-lib.
 */
public final class TokenInvalidation {

    private TokenInvalidation() {}

    /** @param iatSeconds token issued-at in epoch seconds (0 if absent)
     *  @param storedValue raw Redis value; blank/non-numeric is ignored (not invalidated) */
    public static boolean isInvalidated(long iatSeconds, String storedValue) {
        if (storedValue == null || storedValue.isBlank()) return false;
        long invalidatedMs;
        try {
            invalidatedMs = Long.parseLong(storedValue.trim());
        } catch (NumberFormatException e) {
            return false;
        }
        return iatSeconds < invalidatedMs / 1000;
    }
}
