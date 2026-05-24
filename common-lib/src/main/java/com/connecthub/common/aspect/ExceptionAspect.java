package com.connecthub.common.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;

import java.util.Arrays;

/**
 * Logs exceptions thrown from any service-layer method across all ConnectHub microservices.
 *
 * Registered automatically via ConnectHubCommonAutoConfiguration — no @Component needed.
 */
@Aspect
@Slf4j
public class ExceptionAspect {

    @Pointcut("execution(* com.connecthub.*.service.*.*(..))")
    public void serviceMethods() {}

    @AfterThrowing(pointcut = "serviceMethods()", throwing = "ex")
    public void logException(JoinPoint joinPoint, Exception ex) {
        log.error("Exception in [{}.{}] args={} — {}",
                joinPoint.getTarget().getClass().getSimpleName(),
                joinPoint.getSignature().getName(),
                Arrays.toString(joinPoint.getArgs()),
                ex.getMessage());
    }
}
