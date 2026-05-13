package com.connecthub.auth.aspect;

import com.connecthub.auth.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditAspect {

    private final AuditService auditService;

    @Before("@annotation(auditable)")
    public void auditAdminAction(JoinPoint joinPoint, Auditable auditable) {
        int actorId = resolveActorId();
        int entityId = resolveEntityId(joinPoint.getArgs());
        String ip = resolveIp();

        auditService.log(actorId, auditable.action(), auditable.entityType(),
                String.valueOf(entityId), null, ip);
    }

    private int resolveActorId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return -1;
        try {
            return Integer.parseInt(auth.getName());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private int resolveEntityId(Object[] args) {
        for (Object arg : args) {
            if (arg instanceof Integer id) return id;
        }
        return -1;
    }

    private String resolveIp() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest req = attrs.getRequest();
                String xff = req.getHeader("X-Forwarded-For");
                return (xff != null && !xff.isBlank()) ? xff.split(",")[0].trim() : req.getRemoteAddr();
            }
        } catch (Exception ignored) {}
        return "unknown";
    }
}
