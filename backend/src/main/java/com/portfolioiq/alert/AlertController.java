package com.portfolioiq.alert;

import com.portfolioiq.alert.AlertDtos.*;
import com.portfolioiq.common.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {
    private final AlertService service;
    private final CurrentUser currentUser;

    public AlertController(AlertService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AlertResponse create(@Valid @RequestBody CreateAlertRequest req, Authentication auth) {
        return service.create(currentUser.id(auth), req);
    }

    @GetMapping
    public List<AlertResponse> list(Authentication auth) { return service.list(currentUser.id(auth)); }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, Authentication auth) { service.delete(currentUser.id(auth), id); }
}
