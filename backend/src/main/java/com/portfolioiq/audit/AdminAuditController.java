package com.portfolioiq.audit;

import com.portfolioiq.common.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/** Access is restricted to ADMINISTRATOR by the /api/admin/** rule in SecurityConfig. */
@RestController
@RequestMapping("/api/admin/audit-logs")
public class AdminAuditController {
    public record AuditLogResponse(Long id, Long userId, String action, String entityType, String entityId,
                                   String details, String ipAddress, Instant createdAt) {}

    private final AuditLogRepository repo;

    public AdminAuditController(AuditLogRepository repo) { this.repo = repo; }

    @GetMapping
    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> list(@RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "50") int size) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        return PageResponse.of(repo.findAll(pageable).map(a -> new AuditLogResponse(a.getId(), a.getUserId(),
                a.getAction(), a.getEntityType(), a.getEntityId(), a.getDetails(), a.getIpAddress(), a.getCreatedAt())));
    }
}
