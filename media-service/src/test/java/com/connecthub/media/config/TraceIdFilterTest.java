package com.connecthub.media.config;

import com.connecthub.common.config.TraceIdFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class TraceIdFilterTest {

    private final TraceIdFilter filter = new TraceIdFilter();

    @AfterEach
    void cleanup() {
        MDC.clear();
    }

    @Test
    void doFilter_withTraceHeader_usesThat() throws IOException, ServletException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Trace-Id", "abc123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        doAnswer(inv -> {
            assertThat(MDC.get("traceId")).isEqualTo("abc123");
            return null;
        }).when(chain).doFilter(request, response);

        filter.doFilter(request, response, chain);

        // MDC should be cleared after filter
        assertThat(MDC.get("traceId")).isNull();
        verify(chain).doFilter(request, response);
    }

    @Test
    void doFilter_withoutTraceHeader_generatesOne() throws IOException, ServletException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        doAnswer(inv -> {
            String traceId = MDC.get("traceId");
            assertThat(traceId).isNotNull().hasSize(16);
            return null;
        }).when(chain).doFilter(request, response);

        filter.doFilter(request, response, chain);

        assertThat(MDC.get("traceId")).isNull();
    }

    @Test
    void doFilter_chainThrows_mdcStillCleared() throws IOException, ServletException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        doThrow(new ServletException("fail")).when(chain).doFilter(request, response);

        try {
            filter.doFilter(request, response, chain);
        } catch (ServletException ignored) {
        }

        assertThat(MDC.get("traceId")).isNull();
    }
}
