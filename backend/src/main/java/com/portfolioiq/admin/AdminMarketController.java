package com.portfolioiq.admin;

import com.portfolioiq.admin.AdminMarketDtos.*;
import com.portfolioiq.common.CurrentUser;
import com.portfolioiq.market.RefreshResult;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/market")
@PreAuthorize("hasRole('ADMINISTRATOR')")
public class AdminMarketController {
    private final AdminMarketService service;
    private final CurrentUser currentUser;

    public AdminMarketController(AdminMarketService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping
    public MarketAdminView view() { return service.view(); }

    @PutMapping("/settings")
    public MarketAdminView update(@Valid @RequestBody MarketSettingsRequest req, Authentication auth) {
        return service.update(currentUser.id(auth), req.refreshEnabled(), req.maxRequestsPerRefresh());
    }

    @PostMapping("/refresh")
    public RefreshResult refresh(Authentication auth) { return service.refreshNow(currentUser.id(auth)); }
}
