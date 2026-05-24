package com.connecthub.media.aspect;

import com.connecthub.common.aspect.PerformanceAspect;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PerformanceAspectTest {

    @InjectMocks private PerformanceAspect aspect;

    @Test
    void monitorPerformance_fastCall_returnsResult() throws Throwable {
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        when(pjp.proceed()).thenReturn("ok");

        Object result = aspect.monitorPerformance(pjp);

        assertThat(result).isEqualTo("ok");
        verify(pjp).proceed();
    }

    @Test
    void monitorPerformance_slowCall_stillReturnsResult() throws Throwable {
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        when(pjp.proceed()).thenAnswer(inv -> {
            Thread.sleep(10);
            return "delayed";
        });

        Object result = aspect.monitorPerformance(pjp);

        assertThat(result).isEqualTo("delayed");
    }
}
