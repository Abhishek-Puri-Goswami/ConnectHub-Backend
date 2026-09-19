package com.connecthub.room.config;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class InternalFeignConfig {

    /** Proves to the callee that this is a genuine ConnectHub service (see common-lib InternalAuthFilter). */
    @Bean
    public RequestInterceptor internalServiceInterceptor(
            @Value("${connecthub.internal.secret:${INTERNAL_SERVICE_SECRET:}}") String secret) {
        return template -> {
            template.header("X-Internal-Service", "room-service");
            template.header("X-Internal-Auth", secret);
        };
    }
}
