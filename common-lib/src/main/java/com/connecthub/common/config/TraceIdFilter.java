package com.connecthub.common.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;

import java.io.IOException;
import java.util.UUID;

/**
 * Servlet filter that attaches a trace ID to the MDC for every incoming request.
 * If the caller supplies an X-Trace-Id header, that value is reused; otherwise a
 * 16-character hex ID is generated.
 *
 * Registered automatically via ConnectHubCommonAutoConfiguration for all
 * servlet-stack services — no @Component or manual filter registration needed.
 */
@Order(1)
public class TraceIdFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpReq = (HttpServletRequest) request;
        String traceId = httpReq.getHeader("X-Trace-Id");
        if (traceId == null) {
            traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        }
        MDC.put("traceId", traceId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }
}
