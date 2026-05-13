package com.connecthub.media.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoggingAspectTest {

    @InjectMocks private LoggingAspect aspect;

    @Test
    void logServiceCall_proceedsAndReturnsResult() throws Throwable {
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        Signature sig = mock(Signature.class);
        when(pjp.getTarget()).thenReturn(new Object());
        when(pjp.getSignature()).thenReturn(sig);
        when(sig.getName()).thenReturn("testMethod");
        when(pjp.getArgs()).thenReturn(new Object[]{"arg1"});
        when(pjp.proceed()).thenReturn("result");

        Object result = aspect.logServiceCall(pjp);

        assertThat(result).isEqualTo("result");
        verify(pjp).proceed();
    }

    @Test
    void logServiceCall_propagatesException() throws Throwable {
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        Signature sig = mock(Signature.class);
        when(pjp.getTarget()).thenReturn(new Object());
        when(pjp.getSignature()).thenReturn(sig);
        when(sig.getName()).thenReturn("failMethod");
        when(pjp.getArgs()).thenReturn(new Object[]{});
        when(pjp.proceed()).thenThrow(new RuntimeException("boom"));

        assertThatThrownBy(() -> aspect.logServiceCall(pjp))
                .isInstanceOf(RuntimeException.class).hasMessage("boom");
    }
}
