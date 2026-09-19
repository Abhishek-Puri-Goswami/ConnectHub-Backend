package com.connecthub.media.config;

import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Marks media-service's outgoing Feign calls as internal (the gateway strips this header from end users). */
@Configuration
public class InternalFeignConfig {

    @Bean
    public RequestInterceptor internalServiceInterceptor() {
        return template -> template.header("X-Internal-Service", "media-service");
    }
}
