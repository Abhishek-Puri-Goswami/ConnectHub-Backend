package com.connecthub.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Makes the gateway/service trust boundary real.
 *
 * Services identify the caller from headers the api-gateway injects after validating the JWT
 * (X-User-Id, X-User-Role, ...) and recognise each other with X-Internal-Service. Headers are only
 * words, so anything that can reach a service port directly could send them. The gateway (and each
 * service's Feign client) therefore also sends {@code X-Internal-Auth}: a secret shared through the
 * INTERNAL_SERVICE_SECRET environment variable. This filter runs before Spring Security and:
 * <ul>
 *   <li>valid secret: request passes untouched;</li>
 *   <li>missing/wrong secret: every identity/internal header is <b>hidden</b> from the rest of the
 *       request, so it is anonymous (endpoints then answer 401/403). Public paths (health, docs,
 *       media file bytes, which have their own checks) keep working;</li>
 *   <li>sensitive actuator endpoints (everything except health/info) are refused with 403 unless the
 *       secret is valid.</li>
 * </ul>
 */
public class InternalAuthFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Internal-Auth";

    /** Headers that carry identity or internal-caller claims; only trusted with a valid secret. */
    static final Set<String> TRUSTED_HEADERS = Set.of(
            "x-user-id", "x-user-email", "x-user-username", "x-user-role", "x-subscription-tier",
            "x-internal-service", "x-internal-auth");

    private final byte[] secret;

    public InternalAuthFilter(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "INTERNAL_SERVICE_SECRET is not set. Add it to .env (start-backend.ps1 generates one if missing).");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        boolean trusted = isValid(request.getHeader(HEADER));
        if (!trusted && isSensitiveActuator(request)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write("{\"success\":false,\"status\":403,\"error\":\"Forbidden\",\"message\":\"Forbidden\"}");
            return;
        }
        chain.doFilter(trusted ? request : new HideTrustedHeaders(request), response);
    }

    boolean isValid(String presented) {
        return presented != null
                && MessageDigest.isEqual(presented.getBytes(StandardCharsets.UTF_8), secret);
    }

    /** Actuator endpoints other than health/info can change log levels or expose configuration. */
    static boolean isSensitiveActuator(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path == null || !path.startsWith("/actuator")) return false;
        return !(path.equals("/actuator") || path.equals("/actuator/health") || path.startsWith("/actuator/health/")
                || path.equals("/actuator/info"));
    }

    private static final class HideTrustedHeaders extends HttpServletRequestWrapper {
        HideTrustedHeaders(HttpServletRequest request) { super(request); }

        private static boolean hidden(String name) {
            return name != null && TRUSTED_HEADERS.contains(name.toLowerCase());
        }

        @Override public String getHeader(String name) { return hidden(name) ? null : super.getHeader(name); }

        @Override public Enumeration<String> getHeaders(String name) {
            return hidden(name) ? Collections.emptyEnumeration() : super.getHeaders(name);
        }

        @Override public Enumeration<String> getHeaderNames() {
            List<String> visible = Collections.list(super.getHeaderNames()).stream()
                    .filter(n -> !hidden(n)).collect(Collectors.toList());
            return Collections.enumeration(visible);
        }

        @Override public int getIntHeader(String name) { return hidden(name) ? -1 : super.getIntHeader(name); }

        @Override public long getDateHeader(String name) { return hidden(name) ? -1L : super.getDateHeader(name); }
    }
}
