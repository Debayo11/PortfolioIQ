package com.portfolioiq.admin;

import com.portfolioiq.admin.AdminStatsDtos.Statistics;
import com.portfolioiq.admin.AdminSystemDtos.ApiUsage;
import com.portfolioiq.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;

/** Statistics, system logs and API usage for administrators. */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMINISTRATOR')")
public class AdminSystemController {
    private final AdminStatsService stats;
    private final LogCapture logs;
    private final ApiUsageService usage;

    public AdminSystemController(AdminStatsService stats, LogCapture logs, ApiUsageService usage) {
        this.stats = stats;
        this.logs = logs;
        this.usage = usage;
    }

    @GetMapping("/statistics")
    public Statistics statistics() { return stats.statistics(); }

    @GetMapping("/logs")
    public List<LogEntry> logs(@RequestParam(defaultValue = "WARN") String level,
                               @RequestParam(required = false) String q,
                               @RequestParam(defaultValue = "100") int limit) {
        String lv = level.toUpperCase(Locale.ROOT);
        if (!lv.equals("WARN") && !lv.equals("ERROR")) throw new ApiException(HttpStatus.BAD_REQUEST, "level must be WARN or ERROR");
        return logs.buffer().snapshot(lv, q, Math.min(Math.max(limit, 1), 500));
    }

    @GetMapping("/api-usage")
    public ApiUsage apiUsage() { return usage.usage(); }
}
