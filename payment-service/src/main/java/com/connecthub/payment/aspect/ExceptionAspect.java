package com.connecthub.payment.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

/**
 * ExceptionAspect — logs exceptions thrown from service layer methods.
 *
 * Fix: removed Arrays.toString(joinPoint.getArgs()) to prevent PII and financial
 * data (user emails, payment amounts, Razorpay credentials) from appearing in logs.
 * Logs method name and arg count only — sufficient for debugging without exposure.
 */
@Aspect
@Component
@Slf4j
public class ExceptionAspect {

    @Pointcut("execution(* com.connecthub.payment.service.*.*(..))")
    public void serviceMethods() {}

    @AfterThrowing(pointcut = "serviceMethods()", throwing = "ex")
    public void logException(JoinPoint joinPoint, Exception ex) {
        log.error("Exception in [{}.{}] ({} args) — {}",
                joinPoint.getTarget().getClass().getSimpleName(),
                joinPoint.getSignature().getName(),
                joinPoint.getArgs().length,
                ex.getMessage());
    }
}
