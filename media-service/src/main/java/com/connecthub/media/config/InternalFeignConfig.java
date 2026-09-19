package com.connecthub.media.config;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Marks media-service's outgoing Feign calls as internal (the gateway strips this header from end users). */
@Configuration
public class InternalFeignConfig {

    /** Proves to the callee that this is a genuine ConnectHub service (see common-lib InternalAuthFilter). */
    @Bean
    public RequestInterceptor internalServiceInterceptor(
            @Value("${connecthub.internal.secret:${INTERNAL_SERVICE_SECRET:}}") String secret) {
        return template -> {
            template.header("X-Internal-Service", "media-service");
            template.header("X-Internal-Auth", secret);
        };
    }
}
