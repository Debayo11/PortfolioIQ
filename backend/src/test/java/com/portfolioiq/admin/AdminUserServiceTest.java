package com.portfolioiq.admin;

import com.portfolioiq.audit.AuditService;
import com.portfolioiq.common.ApiException;
import com.portfolioiq.portfolio.PortfolioRepository;
import com.portfolioiq.user.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AdminUserServiceTest {
    private static final long ME = 1L, OTHER = 2L;

    private final UserRepository users = mock(UserRepository.class);
    private final RoleRepository roles = mock(RoleRepository.class);
    private final PortfolioRepository portfolios = mock(PortfolioRepository.class);
    private final AuditService audit = mock(AuditService.class);
    private final Role adminRole = mock(Role.class);
    private final Role investorRole = mock(Role.class);
    private AdminUserService service;

    @BeforeEach
    void setUp() {
        when(adminRole.getName()).thenReturn(RoleName.ADMINISTRATOR);
        when(investorRole.getName()).thenReturn(RoleName.INVESTOR);
        when(roles.findByName(RoleName.ADMINISTRATOR)).thenReturn(Optional.of(adminRole));
        when(roles.findByName(RoleName.INVESTOR)).thenReturn(Optional.of(investorRole));
        when(users.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        service = new AdminUserService(users, roles, portfolios, audit);
    }

    private User user(long id, Role... r) {
        User u = new User("u" + id + "@example.com", "hash", "First", "Last");
        ReflectionTestUtils.setField(u, "id", id);
        for (Role role : r) u.getRoles().add(role);
        when(users.findById(id)).thenReturn(Optional.of(u));
        return u;
    }

    private static void assertStatus(HttpStatus expected, Runnable action) {
        assertEquals(expected, assertThrows(ApiException.class, action::run).getStatus());
    }

    @Test void youCannotRemoveYourOwnAdministratorRole() {
        user(ME, adminRole);
        when(users.countEnabledWithRole(RoleName.ADMINISTRATOR)).thenReturn(3L);
        assertStatus(HttpStatus.CONFLICT, () -> service.setRoles(ME, ME, Set.of(RoleName.INVESTOR)));
        verify(users, never()).save(any(User.class));
    }

    @Test void theLastEnabledAdministratorCannotBeDemoted() {
        user(OTHER, adminRole);
        when(users.countEnabledWithRole(RoleName.ADMINISTRATOR)).thenReturn(1L);
        assertStatus(HttpStatus.CONFLICT, () -> service.setRoles(ME, OTHER, Set.of(RoleName.INVESTOR)));
    }

    @Test void anAdministratorCanBeDemotedWhenAnotherRemains() {
        User target = user(OTHER, adminRole);
        when(users.countEnabledWithRole(RoleName.ADMINISTRATOR)).thenReturn(2L);
        var result = service.setRoles(ME, OTHER, Set.of(RoleName.INVESTOR));
        assertEquals(java.util.List.of("INVESTOR"), result.roles());
        assertTrue(target.getRoles().contains(investorRole));
        assertFalse(target.getRoles().contains(adminRole));
        verify(audit).log(eq(ME), eq("ADMIN_USER_ROLES_CHANGED"), any(), any(), any());
    }

    @Test void aUserNeedsAtLeastOneRole() {
        user(OTHER, investorRole);
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.setRoles(ME, OTHER, Set.of()));
    }

    @Test void youCannotDisableYourOwnAccount() {
        user(ME, adminRole);
        assertStatus(HttpStatus.CONFLICT, () -> service.setEnabled(ME, ME, false));
    }

    @Test void theLastEnabledAdministratorCannotBeDisabled() {
        user(OTHER, adminRole);
        when(users.countEnabledWithRole(RoleName.ADMINISTRATOR)).thenReturn(1L);
        assertStatus(HttpStatus.CONFLICT, () -> service.setEnabled(ME, OTHER, false));
    }

    @Test void anOrdinaryUserCanBeDisabledAndReenabled() {
        User target = user(OTHER, investorRole);
        assertFalse(service.setEnabled(ME, OTHER, false).enabled());
        assertFalse(target.isEnabled());
        assertTrue(service.setEnabled(ME, OTHER, true).enabled());
        verify(audit).log(eq(ME), eq("ADMIN_USER_DISABLED"), any(), any(), any());
        verify(audit).log(eq(ME), eq("ADMIN_USER_ENABLED"), any(), any(), any());
    }

    @Test void unknownUsersLookLikeNotFound() {
        when(users.findById(99L)).thenReturn(Optional.empty());
        assertStatus(HttpStatus.NOT_FOUND, () -> service.setEnabled(ME, 99L, false));
    }
}
