package com.connecthub.auth.config;

import org.junit.jupiter.api.Test;
import org.springframework.util.AntPathMatcher;

import static org.junit.jupiter.api.Assertions.*;

class SecurityConfigAuthorizationTest {

    @Test
    void adminRoleCheck_acceptsOnlyAdminRoles() {
        assertTrue(SecurityConfig.isAdminRole("ADMIN"));
        assertTrue(SecurityConfig.isAdminRole("platform_admin"));
        assertFalse(SecurityConfig.isAdminRole("USER"));
        assertFalse(SecurityConfig.isAdminRole(""));
        assertFalse(SecurityConfig.isAdminRole(null));
    }

    @Test
    void publicPaths_coverEveryGatewayOpenAuthEndpoint_andNothingSensitive() {
        AntPathMatcher m = new AntPathMatcher();
        java.util.function.Predicate<String> isPublic = path -> java.util.Arrays.stream(SecurityConfig.PUBLIC_PATHS)
                .anyMatch(p -> m.match(p, path));
        for (String open : new String[]{"/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/login/email/request-otp",
                "/api/v1/auth/login/phone/verify-otp", "/api/v1/auth/public/stats", "/api/v1/auth/verify-registration-otp",
                "/api/v1/auth/resend-registration-otp", "/api/v1/auth/forgot-password", "/api/v1/auth/verify-reset-otp",
                "/api/v1/auth/verify-reset-otp/phone", "/api/v1/auth/reset-password", "/api/v1/auth/refresh",
                "/api/v1/auth/validate", "/api/v1/auth/phone/request-otp", "/api/v1/auth/phone/verify-otp"}) {
            assertTrue(isPublic.test(open), open);
        }
        for (String protectedPath : new String[]{"/api/v1/auth/admin/users", "/api/v1/auth/me", "/api/v1/auth/logout",
                "/api/v1/auth/profile/1", "/api/v1/auth/search", "/api/v1/auth/users/batch", "/api/v1/auth/sessions"}) {
            assertFalse(isPublic.test(protectedPath), protectedPath);
        }
    }
}
