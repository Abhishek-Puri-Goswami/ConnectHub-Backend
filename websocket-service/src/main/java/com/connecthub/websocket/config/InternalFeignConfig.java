package com.connecthub.websocket.config;

import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class InternalFeignConfig {

    @Bean
    public RequestInterceptor internalServiceInterceptor() {
        return template -> template.header("X-Internal-Service", "websocket-service");
    }
}
