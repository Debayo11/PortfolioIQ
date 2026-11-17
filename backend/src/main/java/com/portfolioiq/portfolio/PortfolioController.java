package com.portfolioiq.portfolio;

import com.portfolioiq.common.CurrentUser;
import com.portfolioiq.common.PageResponse;
import com.portfolioiq.finance.TransactionType;
import com.portfolioiq.portfolio.PortfolioDtos.*;
import com.portfolioiq.transaction.TransactionDtos.*;
import com.portfolioiq.transaction.TransactionService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/portfolios")
public class PortfolioController {
    private final PortfolioService portfolios;
    private final PortfolioReportService reports;
    private final TransactionService transactions;
    private final CurrentUser currentUser;

    public PortfolioController(PortfolioService portfolios, PortfolioReportService reports,
                               TransactionService transactions, CurrentUser currentUser) {
        this.portfolios = portfolios;
        this.reports = reports;
        this.transactions = transactions;
        this.currentUser = currentUser;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PortfolioResponse create(@Valid @RequestBody PortfolioRequest req, Authentication auth) {
        return portfolios.create(currentUser.id(auth), req);
    }

    @GetMapping
    public List<PortfolioResponse> list(Authentication auth) { return portfolios.list(currentUser.id(auth)); }

    @GetMapping("/{id}")
    public PortfolioResponse get(@PathVariable Long id, Authentication auth) {
        return portfolios.get(currentUser.id(auth), id);
    }

    @PutMapping("/{id}")
    public PortfolioResponse update(@PathVariable Long id, @Valid @RequestBody PortfolioRequest req, Authentication auth) {
        return portfolios.update(currentUser.id(auth), id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, Authentication auth) { portfolios.delete(currentUser.id(auth), id); }

    @GetMapping("/{id}/summary")
    public PortfolioSummaryResponse summary(@PathVariable Long id, Authentication auth) {
        return reports.summary(currentUser.id(auth), id);
    }

    @GetMapping("/{id}/holdings")
    public List<HoldingResponse> holdings(@PathVariable Long id, Authentication auth) {
        return reports.holdings(currentUser.id(auth), id);
    }

    @PostMapping("/{id}/transactions")
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse record(@PathVariable Long id, @Valid @RequestBody CreateTransactionRequest req,
                                      Authentication auth) {
        return transactions.record(currentUser.id(auth), id, req);
    }

    @GetMapping("/{id}/transactions")
    public PageResponse<TransactionResponse> listTransactions(
            @PathVariable Long id,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) Long assetId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @PageableDefault(size = 20, sort = "executedAt", direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable,
            Authentication auth) {
        return transactions.list(currentUser.id(auth), id, type, assetId, from, to, pageable);
    }

    @DeleteMapping("/{id}/transactions/{transactionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTransaction(@PathVariable Long id, @PathVariable Long transactionId, Authentication auth) {
        transactions.delete(currentUser.id(auth), id, transactionId);
    }
}
