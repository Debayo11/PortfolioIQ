package com.portfolioiq.watchlist;

import com.portfolioiq.common.CurrentUser;
import com.portfolioiq.watchlist.WatchlistDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/watchlists")
public class WatchlistController {
    private final WatchlistService service;
    private final CurrentUser currentUser;

    public WatchlistController(WatchlistService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WatchlistResponse create(@Valid @RequestBody WatchlistRequest req, Authentication auth) {
        return service.create(currentUser.id(auth), req);
    }

    @GetMapping
    public List<WatchlistResponse> list(Authentication auth) { return service.list(currentUser.id(auth)); }

    @GetMapping("/{id}")
    public WatchlistResponse get(@PathVariable Long id, Authentication auth) { return service.get(currentUser.id(auth), id); }

    @PutMapping("/{id}")
    public WatchlistResponse rename(@PathVariable Long id, @Valid @RequestBody WatchlistRequest req, Authentication auth) {
        return service.rename(currentUser.id(auth), id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, Authentication auth) { service.delete(currentUser.id(auth), id); }

    @PostMapping("/{id}/items")
    public WatchlistResponse addItem(@PathVariable Long id, @Valid @RequestBody AddItemRequest req, Authentication auth) {
        return service.addItem(currentUser.id(auth), id, req.assetId());
    }

    @DeleteMapping("/{id}/items/{assetId}")
    public WatchlistResponse removeItem(@PathVariable Long id, @PathVariable Long assetId, Authentication auth) {
        return service.removeItem(currentUser.id(auth), id, assetId);
    }
}
