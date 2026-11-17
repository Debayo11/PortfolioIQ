package com.portfolioiq.transaction;

import com.portfolioiq.common.CurrentUser;
import com.portfolioiq.transaction.TransferDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/transfers")
public class TransferController {
    private final TransferService service;
    private final CurrentUser currentUser;

    public TransferController(TransferService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransferResponse transfer(@Valid @RequestBody TransferRequest req, Authentication auth) {
        return service.transfer(currentUser.id(auth), req);
    }

    @DeleteMapping("/{transferId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void undo(@PathVariable UUID transferId, Authentication auth) {
        service.undo(currentUser.id(auth), transferId);
    }
}
