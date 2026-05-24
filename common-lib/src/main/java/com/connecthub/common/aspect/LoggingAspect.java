package com.connecthub.common.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;

import java.util.Arrays;

/**
 * Logs entry/exit of every service-layer method across all ConnectHub microservices.
 *
 * Registered automatically via ConnectHubCommonAutoConfiguration — no @Component needed.
 */
@Aspect
@Slf4j
public class LoggingAspect {

    @Pointcut("execution(* com.connecthub.*.service.*.*(..))")
    public void serviceMethods() {}

    @Around("serviceMethods()")
    public Object logServiceCall(ProceedingJoinPoint joinPoint) throws Throwable {
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String method    = joinPoint.getSignature().getName();

        log.debug(">> {}.{}() args={}", className, method, Arrays.toString(joinPoint.getArgs()));

        long start  = System.currentTimeMillis();
        Object result = joinPoint.proceed();
        long elapsed = System.currentTimeMillis() - start;

        log.debug("<< {}.{}() completed in {}ms", className, method, elapsed);
        return result;
    }
}
