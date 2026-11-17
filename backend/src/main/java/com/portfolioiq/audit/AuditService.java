package com.portfolioiq.audit;

import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Records security-relevant events. Runs inside the caller's transaction, so a rolled-back
 * operation leaves no audit entry claiming it happened. Never pass secrets or tokens as details.
 */
@Service
public class AuditService {
    private final AuditLogRepository repo;

    public AuditService(AuditLogRepository repo) { this.repo = repo; }

    public void log(Long userId, String action, String entityType, String entityId, String details) {
        repo.save(new AuditLog(userId, action, entityType, entityId, truncate(details, 1000), clientIp()));
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }

    /** Uses the socket address; X-Forwarded-For is deliberately ignored because clients can forge it. */
    private static String clientIp() {
        var attrs = RequestContextHolder.getRequestAttributes();
        return attrs instanceof ServletRequestAttributes s ? s.getRequest().getRemoteAddr() : null;
    }
}
