package com.connecthub.payment.aspect;

import com.connecthub.common.aspect.LoggingAspect;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoggingAspectTest {

    @Mock private ProceedingJoinPoint joinPoint;
    @Mock private Signature signature;

    @InjectMocks private LoggingAspect aspect;

    @Test
    void logServiceCall_returnsResult() throws Throwable {
        when(joinPoint.proceed()).thenReturn("subscription");
        when(joinPoint.getTarget()).thenReturn(new Object());
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getName()).thenReturn("getSubscription");
        when(joinPoint.getArgs()).thenReturn(new Object[]{1});

        assertEquals("subscription", aspect.logServiceCall(joinPoint));
    }

    @Test
    void logServiceCall_noArgs_ok() throws Throwable {
        when(joinPoint.proceed()).thenReturn(null);
        when(joinPoint.getTarget()).thenReturn(new Object());
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getName()).thenReturn("expire");
        when(joinPoint.getArgs()).thenReturn(new Object[0]);

        assertNull(aspect.logServiceCall(joinPoint));
    }

    @Test
    void logServiceCall_propagatesException() throws Throwable {
        when(joinPoint.proceed()).thenThrow(new RuntimeException("err"));
        when(joinPoint.getTarget()).thenReturn(new Object());
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getName()).thenReturn("fail");
        when(joinPoint.getArgs()).thenReturn(new Object[0]);

        assertThrows(RuntimeException.class, () -> aspect.logServiceCall(joinPoint));
    }
}
