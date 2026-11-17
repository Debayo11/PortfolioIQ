package com.portfolioiq.asset;

import com.portfolioiq.asset.AssetDtos.*;
import com.portfolioiq.common.CurrentUser;
import com.portfolioiq.common.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/assets")
public class AssetController {
    private final AssetService service;
    private final CurrentUser currentUser;

    public AssetController(AssetService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping
    public PageResponse<AssetResponse> search(@RequestParam(required = false) String q,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return service.search(q, page, size);
    }

    @GetMapping("/{id}")
    public AssetResponse get(@PathVariable Long id) { return service.get(id); }

    /** The asset catalogue is shared, so only administrators and analysts may add to it. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMINISTRATOR','ANALYST')")
    public AssetResponse create(@Valid @RequestBody CreateAssetRequest req, Authentication auth) {
        return service.create(currentUser.id(auth), req);
    }
}
