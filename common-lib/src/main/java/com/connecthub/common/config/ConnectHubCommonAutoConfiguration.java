package com.connecthub.common.config;

import com.connecthub.common.aspect.ExceptionAspect;
import com.connecthub.common.aspect.LoggingAspect;
import com.connecthub.common.aspect.PerformanceAspect;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * Spring Boot auto-configuration for ConnectHub cross-cutting concerns.
 *
 * Any service that declares common-lib as a Maven dependency automatically gets:
 *   - PerformanceAspect  — WARN logs for slow service calls
 *   - LoggingAspect      — DEBUG entry/exit logs for all service calls
 *   - ExceptionAspect    — ERROR logs for unhandled service exceptions
 *   - TraceIdFilter      — MDC trace-ID propagation (servlet stack only)
 *
 * No @ComponentScan change is needed in any service — Spring Boot discovers this
 * class via META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports.
 */
@AutoConfiguration
@EnableAspectJAutoProxy
public class ConnectHubCommonAutoConfiguration {

    @Bean
    public PerformanceAspect performanceAspect() {
        return new PerformanceAspect();
    }

    @Bean
    public LoggingAspect loggingAspect() {
        return new LoggingAspect();
    }

    @Bean
    public ExceptionAspect exceptionAspect() {
        return new ExceptionAspect();
    }

    /**
     * TraceIdFilter is only meaningful on servlet-stack services.
     * Reactive services (api-gateway) are excluded automatically.
     */
    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    public FilterRegistrationBean<TraceIdFilter> traceIdFilter() {
        FilterRegistrationBean<TraceIdFilter> reg = new FilterRegistrationBean<>(new TraceIdFilter());
        reg.setOrder(1);
        reg.addUrlPatterns("/*");
        return reg;
    }
}
