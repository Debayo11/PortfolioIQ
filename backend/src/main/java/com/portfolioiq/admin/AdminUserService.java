package com.portfolioiq.admin;

import com.portfolioiq.admin.AdminUserDtos.UserSummary;
import com.portfolioiq.audit.AuditService;
import com.portfolioiq.common.ApiException;
import com.portfolioiq.common.PageResponse;
import com.portfolioiq.portfolio.PortfolioRepository;
import com.portfolioiq.user.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Account management for administrators. Guard rails: you cannot lock yourself out, and the system can never be left
 * without an enabled administrator. Changes take effect immediately because roles and the enabled flag are read
 * from the database on every request rather than from the token.
 */
@Service
public class AdminUserService {
    private final UserRepository users;
    private final RoleRepository roles;
    private final PortfolioRepository portfolios;
    private final AuditService audit;

    public AdminUserService(UserRepository users, RoleRepository roles, PortfolioRepository portfolios, AuditService audit) {
        this.users = users;
        this.roles = roles;
        this.portfolios = portfolios;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public PageResponse<UserSummary> list(String q, RoleName role, Boolean enabled, int page, int size) {
        Specification<User> spec = (root, query, cb) -> cb.conjunction();
        if (q != null && !q.isBlank()) {
            String pattern = "%" + escapeLike(q.trim().toLowerCase(Locale.ROOT)) + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.<String>get("email")), pattern, '\\'),
                    cb.like(cb.lower(root.<String>get("firstName")), pattern, '\\'),
                    cb.like(cb.lower(root.<String>get("lastName")), pattern, '\\')));
        }
        if (role != null) {
            spec = spec.and((root, query, cb) -> {
                if (query != null) query.distinct(true);
                return cb.equal(root.join("roles").get("name"), role);
            });
        }
        if (enabled != null) spec = spec.and((root, query, cb) -> cb.equal(root.get("enabled"), enabled));

        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        Page<User> result = users.findAll(spec, pageable);

        List<Long> ids = result.getContent().stream().map(User::getId).toList();
        Map<Long, Long> counts = new HashMap<>();
        if (!ids.isEmpty()) {
            for (Object[] row : portfolios.countByUserIds(ids)) counts.put((Long) row[0], ((Number) row[1]).longValue());
        }
        return PageResponse.of(result.map(u -> UserSummary.of(u, counts.getOrDefault(u.getId(), 0L))));
    }

    @Transactional
    public UserSummary setRoles(Long adminId, Long targetId, Set<RoleName> wanted) {
        if (wanted == null || wanted.isEmpty()) throw bad("A user needs at least one role");
        User target = find(targetId);
        boolean hadAdmin = isAdministrator(target);
        if (hadAdmin && !wanted.contains(RoleName.ADMINISTRATOR)) {
            if (Objects.equals(adminId, targetId)) throw conflict("You cannot remove your own administrator role");
            if (target.isEnabled() && users.countEnabledWithRole(RoleName.ADMINISTRATOR) <= 1) {
                throw conflict("At least one enabled administrator must remain");
            }
        }
        List<Role> resolved = new ArrayList<>();
        for (RoleName name : wanted) {
            resolved.add(roles.findByName(name).orElseThrow(() -> bad("Unknown role " + name)));
        }
        target.replaceRoles(resolved);
        users.save(target);
        audit.log(adminId, "ADMIN_USER_ROLES_CHANGED", "USER", String.valueOf(targetId),
                "roles=" + wanted.stream().map(Enum::name).sorted().collect(Collectors.joining(",")));
        return UserSummary.of(target, portfolios.countByUserId(targetId));
    }

    @Transactional
    public UserSummary setEnabled(Long adminId, Long targetId, boolean enabled) {
        User target = find(targetId);
        if (target.isEnabled() == enabled) return UserSummary.of(target, portfolios.countByUserId(targetId));
        if (!enabled) {
            if (Objects.equals(adminId, targetId)) throw conflict("You cannot disable your own account");
            if (isAdministrator(target) && users.countEnabledWithRole(RoleName.ADMINISTRATOR) <= 1) {
                throw conflict("At least one enabled administrator must remain");
            }
        }
        target.setEnabled(enabled);
        users.save(target);
        audit.log(adminId, enabled ? "ADMIN_USER_ENABLED" : "ADMIN_USER_DISABLED", "USER", String.valueOf(targetId), null);
        return UserSummary.of(target, portfolios.countByUserId(targetId));
    }

    private User find(Long id) {
        return users.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private static boolean isAdministrator(User u) {
        return u.getRoles().stream().anyMatch(r -> r.getName() == RoleName.ADMINISTRATOR);
    }

    /** Makes %, _ and \ in the search text literal so they cannot act as wildcards. */
    private static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static ApiException bad(String m) { return new ApiException(HttpStatus.BAD_REQUEST, m); }
    private static ApiException conflict(String m) { return new ApiException(HttpStatus.CONFLICT, m); }
}
