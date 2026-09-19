package com.connecthub.auth.config;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TokenInvalidationTest {
    @Test void tokenIssuedBeforeEvent_isInvalidated() { assertTrue(TokenInvalidation.isInvalidated(1000, "2000000")); }
    @Test void tokenIssuedAfterEvent_isValid() { assertFalse(TokenInvalidation.isInvalidated(3000, "2000000")); }
    @Test void sameSecond_isAccepted() { assertFalse(TokenInvalidation.isInvalidated(2000, "2000500")); }
    @Test void missingOrBlankValue_isValid() {
        assertFalse(TokenInvalidation.isInvalidated(1, null));
        assertFalse(TokenInvalidation.isInvalidated(1, " "));
    }
    @Test void nonNumericValue_isIgnored() { assertFalse(TokenInvalidation.isInvalidated(1, "abc")); }
    @Test void missingIat_isInvalidatedWhenEventExists() { assertTrue(TokenInvalidation.isInvalidated(0, "5000")); }
}
