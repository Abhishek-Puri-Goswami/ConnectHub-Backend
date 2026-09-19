package com.connecthub.admin.config;

import de.codecentric.boot.admin.server.web.client.HttpHeadersProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;

/**
 * Services only serve their sensitive actuator endpoints (metrics, loggers, env) to callers holding the
 * shared INTERNAL_SERVICE_SECRET; the admin server is one, so it sends it on every instance request.
 */
@Configuration
public class InternalAuthHeaderConfig {

    @Bean
    public HttpHeadersProvider internalAuthHeaders(
            @Value("${connecthub.internal.secret:${INTERNAL_SERVICE_SECRET:}}") String secret) {
        return instance -> {
            HttpHeaders headers = new HttpHeaders();
            if (secret != null && !secret.isBlank()) headers.set("X-Internal-Auth", secret);
            return headers;
        };
    }
}
