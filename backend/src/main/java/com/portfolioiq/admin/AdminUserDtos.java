package com.portfolioiq.admin;

import com.portfolioiq.user.RoleName;
import com.portfolioiq.user.User;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public final class AdminUserDtos {
    private AdminUserDtos() {}

    /** Never includes the password hash or any token. */
    public record UserSummary(Long id, String email, String firstName, String lastName, boolean enabled,
                              boolean emailVerified, List<String> roles, Instant createdAt, long portfolioCount) {
        static UserSummary of(User u, long portfolioCount) {
            return new UserSummary(u.getId(), u.getEmail(), u.getFirstName(), u.getLastName(), u.isEnabled(),
                    u.isEmailVerified(), u.getRoles().stream().map(r -> r.getName().name()).sorted().toList(),
                    u.getCreatedAt(), portfolioCount);
        }
    }

    public record UpdateRolesRequest(@NotEmpty Set<RoleName> roles) {}

    public record UpdateEnabledRequest(@NotNull Boolean enabled) {}
}
