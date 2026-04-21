package com.connecthub.gateway.filter;

import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * LoggingFilter — Request/Response Logging for the API Gateway
 *
 * PURPOSE:
 *   Logs every HTTP request and its corresponding response (or error) that
 *   passes through the API Gateway. Useful for debugging routing issues,
 *   tracing slow requests, or investigating unexpected status codes.
 *
 * HOW IT WORKS:
 *   This is a WebFilter (not a GlobalFilter like the other gateway filters).
 *   WebFilter is Spring WebFlux's low-level filter interface that wraps the
 *   entire reactive chain with callbacks:
 *     - Before passing to the chain: logs the HTTP method and full URI.
 *     - doOnSuccess: fires when the response is committed successfully — logs
 *       the HTTP status code.
 *     - doOnError: fires if any unhandled exception propagates through the
 *       chain — logs the error message.
 *
 * WHY System.out.println INSTEAD OF A LOGGER:
 *   This is a simple development-time logging filter. In production you would
 *   replace this with a proper SLF4J logger (log.info / log.error) and include
 *   the X-Trace-Id header in the log line so entries can be correlated across
 *   microservices. For now, stdout is enough for local debugging.
 *
 * NOTE ON REACTIVE LOGGING:
 *   The "GATEWAY REQ" line is logged synchronously before the reactive chain starts.
 *   The "GATEWAY RES" and "GATEWAY ERR" lines are logged in reactive callbacks
 *   (doOnSuccess / doOnError), which execute on the event loop thread when the
 *   response is eventually committed. This means response logs may appear slightly
 *   out of order relative to request logs for concurrent requests.
 */
@Component
public class LoggingFilter implements WebFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        /* Log the incoming request — method (GET, POST, etc.) and the full URI */
        System.out.println("GATEWAY REQ: " + exchange.getRequest().getMethod() + " " + exchange.getRequest().getURI());

        return chain.filter(exchange)
                .doOnSuccess(v -> {
                    /* Log the HTTP response status code after the response is written */
                    System.out.println("GATEWAY RES: " + exchange.getResponse().getStatusCode());
                })
                .doOnError(err -> {
                    /* Log any unhandled exception that propagated to this filter level */
                    System.out.println("GATEWAY ERR: " + err.getMessage());
                });
    }
}
