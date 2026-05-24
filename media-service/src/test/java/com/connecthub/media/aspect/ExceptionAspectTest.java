package com.connecthub.media.aspect;

import com.connecthub.common.aspect.ExceptionAspect;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExceptionAspectTest {

    @InjectMocks private ExceptionAspect aspect;

    @Test
    void logException_doesNotThrow() {
        JoinPoint jp = mock(JoinPoint.class);
        Signature sig = mock(Signature.class);
        when(jp.getTarget()).thenReturn(new Object());
        when(jp.getSignature()).thenReturn(sig);
        when(sig.getName()).thenReturn("failingMethod");
        when(jp.getArgs()).thenReturn(new Object[]{"arg1", 42});

        // Should just log and not throw
        aspect.logException(jp, new RuntimeException("test error"));
        verify(jp).getSignature();
    }

    @Test
    void logException_withEmptyArgs() {
        JoinPoint jp = mock(JoinPoint.class);
        Signature sig = mock(Signature.class);
        when(jp.getTarget()).thenReturn(new Object());
        when(jp.getSignature()).thenReturn(sig);
        when(sig.getName()).thenReturn("noArgMethod");
        when(jp.getArgs()).thenReturn(new Object[]{});

        aspect.logException(jp, new IllegalArgumentException("bad arg"));
        verify(jp).getSignature();
    }
}
