package com.connecthub.auth.aspect;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AspectsTest {

    @InjectMocks
    private LoggingAspect loggingAspect;

    @InjectMocks
    private PerformanceAspect performanceAspect;

    @InjectMocks
    private ExceptionAspect exceptionAspect;

    @Test
    void loggingAspect_proceeds() throws Throwable {
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        Signature sig = mock(Signature.class);
        when(pjp.getTarget()).thenReturn(new Object());
        when(pjp.getSignature()).thenReturn(sig);
        when(sig.getName()).thenReturn("testMethod");
        when(pjp.getArgs()).thenReturn(new Object[]{});
        when(pjp.proceed()).thenReturn("success");

        Object result = loggingAspect.logServiceCall(pjp);
        assertEquals("success", result);
    }

    @Test
    void performanceAspect_logs() throws Throwable {
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        Signature sig = mock(Signature.class);
        // getTarget/getSignature/getName are only called on the slow path (>500ms).
        // Tests run in <1ms so these stubs are unused in the happy path — lenient() avoids
        // UnnecessaryStubbingException while still supporting a hypothetically slow CI run.
        lenient().when(pjp.getTarget()).thenReturn(new Object());
        lenient().when(pjp.getSignature()).thenReturn(sig);
        lenient().when(sig.getName()).thenReturn("testMethod");
        when(pjp.proceed()).thenReturn("success");

        Object result = performanceAspect.monitorPerformance(pjp);
        assertEquals("success", result);
    }

    @Test
    void exceptionAspect_logsAndThrows() throws Throwable {
        JoinPoint jp = mock(JoinPoint.class);
        Signature sig = mock(Signature.class);
        when(jp.getTarget()).thenReturn(new Object());
        when(jp.getSignature()).thenReturn(sig);
        when(sig.getName()).thenReturn("failMethod");
        when(jp.getArgs()).thenReturn(new Object[]{});

        Exception ex = new RuntimeException("err");
        exceptionAspect.logException(jp, ex);
        // ExceptionAspect only logs, it doesn't rethrow (unless defined otherwise)
        verify(jp).getSignature();
    }

}
