package com.connecthub.room.aspect;

import com.connecthub.common.aspect.ExceptionAspect;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExceptionAspectTest {

    @Mock private JoinPoint joinPoint;
    @Mock private Signature signature;

    @InjectMocks private ExceptionAspect aspect;

    @Test
    void logException_logsWithoutThrowing() {
        when(joinPoint.getTarget()).thenReturn(new Object());
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getName()).thenReturn("createRoom");
        when(joinPoint.getArgs()).thenReturn(new Object[]{"arg"});

        aspect.logException(joinPoint, new RuntimeException("room error"));

        verify(joinPoint).getSignature();
    }

    @Test
    void logException_nullMessage_handlesGracefully() {
        when(joinPoint.getTarget()).thenReturn(new Object());
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getName()).thenReturn("deleteRoom");
        when(joinPoint.getArgs()).thenReturn(new Object[0]);

        aspect.logException(joinPoint, new RuntimeException((String) null));
        verify(joinPoint).getSignature();
    }
}
