package com.connecthub.websocket.aspect;

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
        when(joinPoint.proceed()).thenReturn("result");
        when(joinPoint.getTarget()).thenReturn(new Object());
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getName()).thenReturn("doSomething");
        when(joinPoint.getArgs()).thenReturn(new Object[]{"arg1"});

        Object result = aspect.logServiceCall(joinPoint);

        assertEquals("result", result);
        verify(joinPoint).proceed();
    }

    @Test
    void logServiceCall_noArgs_logsEmpty() throws Throwable {
        when(joinPoint.proceed()).thenReturn(42);
        when(joinPoint.getTarget()).thenReturn(new Object());
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getName()).thenReturn("count");
        when(joinPoint.getArgs()).thenReturn(new Object[0]);

        Object result = aspect.logServiceCall(joinPoint);

        assertEquals(42, result);
    }

    @Test
    void logServiceCall_propagatesException() throws Throwable {
        when(joinPoint.proceed()).thenThrow(new RuntimeException("boom"));
        when(joinPoint.getTarget()).thenReturn(new Object());
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getName()).thenReturn("failingMethod");
        when(joinPoint.getArgs()).thenReturn(new Object[0]);

        assertThrows(RuntimeException.class, () -> aspect.logServiceCall(joinPoint));
    }
}
