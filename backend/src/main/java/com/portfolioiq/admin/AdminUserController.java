package com.portfolioiq.admin;

import com.portfolioiq.admin.AdminUserDtos.*;
import com.portfolioiq.common.CurrentUser;
import com.portfolioiq.common.PageResponse;
import com.portfolioiq.user.RoleName;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMINISTRATOR')")
public class AdminUserController {
    private final AdminUserService service;
    private final CurrentUser currentUser;

    public AdminUserController(AdminUserService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping
    public PageResponse<UserSummary> list(@RequestParam(required = false) String q,
                                          @RequestParam(required = false) RoleName role,
                                          @RequestParam(required = false) Boolean enabled,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        return service.list(q, role, enabled, page, size);
    }

    @PutMapping("/{id}/roles")
    public UserSummary roles(@PathVariable Long id, @Valid @RequestBody UpdateRolesRequest req, Authentication auth) {
        return service.setRoles(currentUser.id(auth), id, req.roles());
    }

    @PutMapping("/{id}/enabled")
    public UserSummary enabled(@PathVariable Long id, @Valid @RequestBody UpdateEnabledRequest req, Authentication auth) {
        return service.setEnabled(currentUser.id(auth), id, req.enabled());
    }
}
