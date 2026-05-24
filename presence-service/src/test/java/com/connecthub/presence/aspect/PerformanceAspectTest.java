package com.connecthub.presence.aspect;

import com.connecthub.common.aspect.PerformanceAspect;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PerformanceAspectTest {

    @Mock private ProceedingJoinPoint joinPoint;

    @InjectMocks private PerformanceAspect aspect;

    @Test
    void monitorPerformance_returnsResult() throws Throwable {
        when(joinPoint.proceed()).thenReturn("presence");

        assertEquals("presence", aspect.monitorPerformance(joinPoint));
    }

    @Test
    void monitorPerformance_nullReturn_passesThrough() throws Throwable {
        when(joinPoint.proceed()).thenReturn(null);

        assertNull(aspect.monitorPerformance(joinPoint));
    }

    @Test
    void monitorPerformance_propagatesException() throws Throwable {
        when(joinPoint.proceed()).thenThrow(new RuntimeException("presence error"));
        assertThrows(RuntimeException.class, () -> aspect.monitorPerformance(joinPoint));
    }
}
