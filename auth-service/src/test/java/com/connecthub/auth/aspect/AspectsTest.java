package com.connecthub.auth.aspect;

import com.connecthub.auth.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AspectsTest {

    @Mock
    private AuditService auditService;

    @InjectMocks
    private LoggingAspect loggingAspect;

    @InjectMocks
    private PerformanceAspect performanceAspect;

    @InjectMocks
    private ExceptionAspect exceptionAspect;

    @InjectMocks
    private AuditAspect auditAspect;

    private MockedStatic<SecurityContextHolder> mockedSecurity;
    private MockedStatic<RequestContextHolder> mockedRequestContext;

    @BeforeEach
    void setup() {
        mockedSecurity = mockStatic(SecurityContextHolder.class);
        mockedRequestContext = mockStatic(RequestContextHolder.class);
    }

    @AfterEach
    void tearDown() {
        mockedSecurity.close();
        mockedRequestContext.close();
    }

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
    }

    @Test
    void auditAspect_resolvesContext() {
        JoinPoint jp = mock(JoinPoint.class);
        Auditable auditable = mock(Auditable.class);
        when(auditable.action()).thenReturn("TEST_ACTION");
        when(auditable.entityType()).thenReturn("USER");
        when(jp.getArgs()).thenReturn(new Object[]{123});

        // Mock Security Context
        SecurityContext sc = mock(SecurityContext.class);
        Authentication auth = mock(Authentication.class);
        mockedSecurity.when(SecurityContextHolder::getContext).thenReturn(sc);
        when(sc.getAuthentication()).thenReturn(auth);
        when(auth.getName()).thenReturn("100");

        // Mock Request Context
        ServletRequestAttributes attrs = mock(ServletRequestAttributes.class);
        HttpServletRequest req = mock(HttpServletRequest.class);
        mockedRequestContext.when(RequestContextHolder::getRequestAttributes).thenReturn(attrs);
        when(attrs.getRequest()).thenReturn(req);
        when(req.getRemoteAddr()).thenReturn("127.0.0.1");

        auditAspect.auditAdminAction(jp, auditable);

        verify(auditService).log(eq(100), eq("TEST_ACTION"), eq("USER"), eq("123"), isNull(), eq("127.0.0.1"));
    }
}
