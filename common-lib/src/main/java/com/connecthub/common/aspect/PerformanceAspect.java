package com.connecthub.common.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;

/**
 * Monitors execution time of every service-layer method across all ConnectHub microservices.
 * Logs a WARN when a call exceeds SLOW_THRESHOLD_MS.
 *
 * Registered automatically via ConnectHubCommonAutoConfiguration — no @Component needed.
 */
@Aspect
@Slf4j
public class PerformanceAspect {

    private static final long SLOW_THRESHOLD_MS = 500;

    /** Matches any method in any ConnectHub service's service package. */
    @Pointcut("execution(* com.connecthub.*.service.*.*(..))")
    public void serviceMethods() {}

    @Around("serviceMethods()")
    public Object monitorPerformance(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.currentTimeMillis();
        Object result = joinPoint.proceed();
        long elapsed = System.currentTimeMillis() - start;

        if (elapsed > SLOW_THRESHOLD_MS) {
            log.warn("SLOW_METHOD [{}.{}] took {}ms — threshold {}ms",
                    joinPoint.getTarget().getClass().getSimpleName(),
                    joinPoint.getSignature().getName(),
                    elapsed, SLOW_THRESHOLD_MS);
        }
        return result;
    }
}
