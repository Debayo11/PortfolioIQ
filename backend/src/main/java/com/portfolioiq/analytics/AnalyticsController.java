package com.portfolioiq.analytics;

import com.portfolioiq.analytics.AnalyticsDtos.*;
import com.portfolioiq.common.CurrentUser;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only analytics for the signed-in user. Omit portfolioId to combine all portfolios in one currency
 * (use currency to choose which). Ranges: 24H, 7D, 30D, 3M, 6M, 1Y, ALL (daily resolution).
 */
@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {
    private final AnalyticsService service;
    private final CurrentUser currentUser;

    public AnalyticsController(AnalyticsService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping("/dashboard")
    public Dashboard dashboard(@RequestParam(required = false) Long portfolioId,
                               @RequestParam(required = false) String currency,
                               @RequestParam(defaultValue = "30D") String range, Authentication auth) {
        return service.dashboard(currentUser.id(auth), portfolioId, currency, range);
    }

    @GetMapping("/performance")
    public PerformanceReport performance(@RequestParam(required = false) Long portfolioId,
                                         @RequestParam(required = false) String currency,
                                         @RequestParam(defaultValue = "30D") String range,
                                         @RequestParam(required = false) Long benchmarkAssetId, Authentication auth) {
        return service.performance(currentUser.id(auth), portfolioId, currency, range, benchmarkAssetId);
    }

    @GetMapping("/allocation")
    public AllocationReport allocation(@RequestParam(required = false) Long portfolioId,
                                       @RequestParam(required = false) String currency, Authentication auth) {
        return service.allocation(currentUser.id(auth), portfolioId, currency);
    }

    @GetMapping("/assets")
    public List<AssetPerformance> assets(@RequestParam(required = false) Long portfolioId,
                                         @RequestParam(required = false) String currency, Authentication auth) {
        return service.assetPerformance(currentUser.id(auth), portfolioId, currency);
    }

    @GetMapping("/compare")
    public ComparisonReport compare(@RequestParam List<Long> portfolioIds,
                                    @RequestParam(defaultValue = "30D") String range, Authentication auth) {
        return service.compare(currentUser.id(auth), portfolioIds, range);
    }
}
