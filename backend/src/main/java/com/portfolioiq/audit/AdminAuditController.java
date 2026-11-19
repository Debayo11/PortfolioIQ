package com.portfolioiq.audit;

import com.portfolioiq.common.PageResponse;
import com.portfolioiq.user.User;
import com.portfolioiq.user.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Activity monitoring for administrators (also enforced by the /api/admin/** rule in SecurityConfig). */
@RestController
@RequestMapping("/api/admin/audit-logs")
@PreAuthorize("hasRole('ADMINISTRATOR')")
public class AdminAuditController {
    public record AuditLogResponse(Long id, Long userId, String userEmail, String action, String entityType,
                                   String entityId, String details, String ipAddress, Instant createdAt) {}

    private final AuditLogRepository repo;
    private final UserRepository users;

    public AdminAuditController(AuditLogRepository repo, UserRepository users) {
        this.repo = repo;
        this.users = users;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> list(@RequestParam(required = false) Long userId,
                                               @RequestParam(required = false) String action,
                                               @RequestParam(required = false) Instant from,
                                               @RequestParam(required = false) Instant to,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "50") int size) {
        Specification<AuditLog> spec = (root, q, cb) -> cb.conjunction();
        if (userId != null) spec = spec.and((root, q, cb) -> cb.equal(root.get("userId"), userId));
        if (action != null && !action.isBlank()) spec = spec.and((root, q, cb) -> cb.equal(root.get("action"), action.trim()));
        if (from != null) spec = spec.and((root, q, cb) -> cb.greaterThanOrEqualTo(root.<Instant>get("createdAt"), from));
        if (to != null) spec = spec.and((root, q, cb) -> cb.lessThanOrEqualTo(root.<Instant>get("createdAt"), to));

        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        var result = repo.findAll(spec, pageable);

        Set<Long> ids = new HashSet<>();
        result.getContent().forEach(a -> { if (a.getUserId() != null) ids.add(a.getUserId()); });
        Map<Long, String> emails = ids.isEmpty() ? Map.of()
                : users.findAllById(ids).stream().collect(Collectors.toMap(User::getId, User::getEmail));
        return PageResponse.of(result.map(a -> new AuditLogResponse(a.getId(), a.getUserId(),
                a.getUserId() == null ? null : emails.get(a.getUserId()), a.getAction(), a.getEntityType(),
                a.getEntityId(), a.getDetails(), a.getIpAddress(), a.getCreatedAt())));
    }

    /** Distinct action names, for the filter dropdown. */
    @GetMapping("/actions")
    @Transactional(readOnly = true)
    public List<String> actions() { return repo.distinctActions(); }
}
