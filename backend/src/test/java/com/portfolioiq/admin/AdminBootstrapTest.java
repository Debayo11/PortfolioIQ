package com.portfolioiq.admin;

import com.portfolioiq.audit.AuditService;
import com.portfolioiq.user.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AdminBootstrapTest {
    private final UserRepository users = mock(UserRepository.class);
    private final RoleRepository roles = mock(RoleRepository.class);
    private final AuditService audit = mock(AuditService.class);
    private final Role adminRole = mock(Role.class);

    @BeforeEach
    void setUp() {
        when(adminRole.getName()).thenReturn(RoleName.ADMINISTRATOR);
        when(roles.findByName(RoleName.ADMINISTRATOR)).thenReturn(Optional.of(adminRole));
        when(users.countEnabledWithRole(RoleName.ADMINISTRATOR)).thenReturn(0L);
    }

    private User user(String email, boolean verified) {
        User u = new User(email, "hash", "A", "B");
        ReflectionTestUtils.setField(u, "id", 5L);
        if (verified) u.markEmailVerified();
        return u;
    }

    @Test void grantsTheRoleToTheVerifiedConfiguredAccount() {
        var bootstrap = new AdminBootstrap(users, roles, audit, "Boss@Example.com");
        User u = user("boss@example.com", true);
        assertTrue(bootstrap.promoteIfEligible(u));
        assertTrue(u.getRoles().contains(adminRole));
        verify(audit).log(eq(5L), eq("ADMIN_BOOTSTRAPPED"), any(), any(), any());
    }

    @Test void anUnverifiedAddressCannotBeClaimed() {
        var bootstrap = new AdminBootstrap(users, roles, audit, "boss@example.com");
        User u = user("boss@example.com", false);
        assertFalse(bootstrap.promoteIfEligible(u));
        assertTrue(u.getRoles().isEmpty());
    }

    @Test void otherAddressesAreIgnored() {
        var bootstrap = new AdminBootstrap(users, roles, audit, "boss@example.com");
        assertFalse(bootstrap.promoteIfEligible(user("someone@example.com", true)));
    }

    @Test void nothingHappensOnceAnAdministratorExists() {
        when(users.countEnabledWithRole(RoleName.ADMINISTRATOR)).thenReturn(1L);
        var bootstrap = new AdminBootstrap(users, roles, audit, "boss@example.com");
        assertFalse(bootstrap.promoteIfEligible(user("boss@example.com", true)));
        verify(users, never()).save(any(User.class));
    }

    @Test void disabledWhenNoAddressIsConfigured() {
        var bootstrap = new AdminBootstrap(users, roles, audit, "");
        assertFalse(bootstrap.promoteIfEligible(user("boss@example.com", true)));
    }
}
