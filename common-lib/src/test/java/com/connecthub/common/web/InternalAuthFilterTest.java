package com.connecthub.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class InternalAuthFilterTest {

    private static final String SECRET = "s3cret-value-for-tests";
    private final InternalAuthFilter filter = new InternalAuthFilter(SECRET);
    private final FilterChain chain = mock(FilterChain.class);

    private MockHttpServletRequest req(String path, String... headers) {
        MockHttpServletRequest r = new MockHttpServletRequest("GET", path);
        r.setRequestURI(path);
        for (int i = 0; i < headers.length; i += 2) r.addHeader(headers[i], headers[i + 1]);
        return r;
    }

    private HttpServletRequest passed(MockHttpServletRequest r, MockHttpServletResponse resp) throws Exception {
        filter.doFilterInternal(r, resp, chain);
        ArgumentCaptor<jakarta.servlet.ServletRequest> cap = ArgumentCaptor.forClass(jakarta.servlet.ServletRequest.class);
        verify(chain).doFilter(cap.capture(), any());
        return (HttpServletRequest) cap.getValue();
    }

    @Test
    void validSecret_keepsIdentityHeaders() throws Exception {
        HttpServletRequest out = passed(req("/api/v1/rooms", "X-Internal-Auth", SECRET, "X-User-Id", "7",
                "X-User-Role", "USER", "X-Internal-Service", "websocket-service"), new MockHttpServletResponse());
        assertEquals("7", out.getHeader("X-User-Id"));
        assertEquals("websocket-service", out.getHeader("X-Internal-Service"));
    }

    @Test
    void noSecret_spoofedIdentityAndInternalHeadersAreHidden_otherHeadersSurvive() throws Exception {
        HttpServletRequest out = passed(req("/api/v1/auth/admin/users", "X-User-Id", "1", "x-user-role", "PLATFORM_ADMIN",
                "X-Internal-Service", "auth-service", "X-Subscription-Tier", "PRO", "X-User-Email", "a@b.c",
                "Content-Type", "application/json", "X-Request-Id", "abc"), new MockHttpServletResponse());
        for (String h : new String[]{"X-User-Id", "X-User-Role", "X-Internal-Service", "X-Subscription-Tier", "X-User-Email"}) {
            assertNull(out.getHeader(h), h);
            assertFalse(out.getHeaders(h).hasMoreElements(), h);
            assertFalse(Collections.list(out.getHeaderNames()).stream().anyMatch(n -> n.equalsIgnoreCase(h)), h);
        }
        assertEquals(-1, out.getIntHeader("X-User-Id"));
        assertEquals("abc", out.getHeader("X-Request-Id"));
        assertEquals("application/json", out.getHeader("Content-Type"));
    }

    @Test
    void wrongOrPartialSecret_isTreatedAsNoSecret() throws Exception {
        for (String bad : new String[]{"wrong", SECRET + "x", SECRET.substring(1), ""}) {
            reset(chain);
            HttpServletRequest out = passed(req("/x", "X-Internal-Auth", bad, "X-User-Id", "1"), new MockHttpServletResponse());
            assertNull(out.getHeader("X-User-Id"), bad);
        }
    }

    @Test
    void sensitiveActuatorEndpoints_need_theSecret() throws Exception {
        for (String p : new String[]{"/actuator/loggers", "/actuator/loggers/ROOT", "/actuator/env", "/actuator/metrics", "/actuator/heapdump"}) {
            reset(chain);
            MockHttpServletResponse resp = new MockHttpServletResponse();
            filter.doFilterInternal(req(p), resp, chain);
            assertEquals(403, resp.getStatus(), p);
            verifyNoInteractions(chain);
        }
        MockHttpServletResponse ok = new MockHttpServletResponse();
        filter.doFilterInternal(req("/actuator/loggers", "X-Internal-Auth", SECRET), ok, chain);
        verify(chain).doFilter(any(), any());
        assertEquals(200, ok.getStatus());
    }

    @Test
    void healthAndInfo_stayPublic_forStartupChecks() throws Exception {
        for (String p : new String[]{"/actuator/health", "/actuator/health/db", "/actuator/info", "/actuator"}) {
            reset(chain);
            MockHttpServletResponse resp = new MockHttpServletResponse();
            filter.doFilterInternal(req(p), resp, chain);
            verify(chain).doFilter(any(), any());
            assertEquals(200, resp.getStatus(), p);
        }
    }

    @Test
    void refusesToStartWithoutASecret() {
        assertThrows(IllegalStateException.class, () -> new InternalAuthFilter(""));
        assertThrows(IllegalStateException.class, () -> new InternalAuthFilter(null));
        assertThrows(IllegalStateException.class, () -> new InternalAuthFilter("   "));
    }
}
