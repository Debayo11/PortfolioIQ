package com.portfolioiq.portfolio;

import com.portfolioiq.audit.AuditService;
import com.portfolioiq.common.ApiException;
import com.portfolioiq.portfolio.PortfolioDtos.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
public class PortfolioService {
    private final PortfolioRepository portfolios;
    private final AuditService audit;

    public PortfolioService(PortfolioRepository portfolios, AuditService audit) {
        this.portfolios = portfolios;
        this.audit = audit;
    }

    @Transactional
    public PortfolioResponse create(Long userId, PortfolioRequest req) {
        String name = req.name().trim();
        if (portfolios.existsByUserIdAndNameIgnoreCase(userId, name)) {
            throw new ApiException(HttpStatus.CONFLICT, "You already have a portfolio with this name");
        }
        String currency = req.baseCurrency() == null ? "USD" : req.baseCurrency().toUpperCase(Locale.ROOT);
        Portfolio saved = portfolios.save(new Portfolio(userId, name, blankToNull(req.description()), currency,
                blankToNull(req.category())));
        audit.log(userId, "PORTFOLIO_CREATED", "PORTFOLIO", String.valueOf(saved.getId()), name);
        return PortfolioResponse.of(saved);
    }

    @Transactional(readOnly = true)
    public List<PortfolioResponse> list(Long userId) {
        return portfolios.findByUserIdOrderByNameAsc(userId).stream().map(PortfolioResponse::of).toList();
    }

    @Transactional(readOnly = true)
    public PortfolioResponse get(Long userId, Long id) {
        return PortfolioResponse.of(owned(userId, id));
    }

    @Transactional
    public PortfolioResponse update(Long userId, Long id, PortfolioRequest req) {
        Portfolio p = owned(userId, id);
        String name = req.name().trim();
        if (!p.getName().equalsIgnoreCase(name) && portfolios.existsByUserIdAndNameIgnoreCase(userId, name)) {
            throw new ApiException(HttpStatus.CONFLICT, "You already have a portfolio with this name");
        }
        p.update(name, blankToNull(req.description()), blankToNull(req.category()));
        audit.log(userId, "PORTFOLIO_UPDATED", "PORTFOLIO", String.valueOf(id), name);
        return PortfolioResponse.of(p);
    }

    /** Deleting a portfolio permanently removes its transactions (database cascade). */
    @Transactional
    public void delete(Long userId, Long id) {
        Portfolio p = owned(userId, id);
        portfolios.delete(p);
        audit.log(userId, "PORTFOLIO_DELETED", "PORTFOLIO", String.valueOf(id), p.getName());
    }

    private Portfolio owned(Long userId, Long id) {
        return portfolios.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Portfolio not found"));
    }

    private static String blankToNull(String s) { return s == null || s.isBlank() ? null : s.trim(); }
}
