package com.portfolioiq.alert;

import com.portfolioiq.alert.AlertDtos.*;
import com.portfolioiq.asset.Asset;
import com.portfolioiq.asset.AssetRepository;
import com.portfolioiq.audit.AuditService;
import com.portfolioiq.common.ApiException;
import com.portfolioiq.market.Quote;
import com.portfolioiq.notification.NotificationService;
import com.portfolioiq.notification.NotificationType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AlertService {
    private static final int MAX_ACTIVE_PER_USER = 100;

    private final PriceAlertRepository alerts;
    private final AssetRepository assets;
    private final NotificationService notifications;
    private final AuditService audit;
    private final Clock clock;

    public AlertService(PriceAlertRepository alerts, AssetRepository assets, NotificationService notifications,
                        AuditService audit, Clock clock) {
        this.alerts = alerts;
        this.assets = assets;
        this.notifications = notifications;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public AlertResponse create(Long userId, CreateAlertRequest req) {
        try {
            AlertRules.validate(req.condition(), req.targetPrice(), req.thresholdPercent());
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
        Asset asset = assets.findById(req.assetId())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Unknown asset"));
        if (alerts.countByUserIdAndActiveTrue(userId) >= MAX_ACTIVE_PER_USER) {
            throw new ApiException(HttpStatus.CONFLICT, "You can have at most " + MAX_ACTIVE_PER_USER + " active alerts");
        }
        PriceAlert saved = alerts.save(new PriceAlert(userId, asset.getId(), req.condition(), req.targetPrice(),
                req.thresholdPercent()));
        audit.log(userId, "ALERT_CREATED", "ALERT", String.valueOf(saved.getId()), asset.getSymbol());
        return toResponse(saved, asset.getSymbol());
    }

    @Transactional(readOnly = true)
    public List<AlertResponse> list(Long userId) {
        List<PriceAlert> mine = alerts.findByUserIdOrderByCreatedAtDesc(userId);
        Map<Long, String> symbols = assets.findAllById(mine.stream().map(PriceAlert::getAssetId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Asset::getId, Asset::getSymbol));
        return mine.stream().map(a -> toResponse(a, symbols.get(a.getAssetId()))).toList();
    }

    @Transactional
    public void delete(Long userId, Long id) {
        PriceAlert alert = alerts.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Alert not found"));
        alerts.delete(alert);
        audit.log(userId, "ALERT_DELETED", "ALERT", String.valueOf(id), null);
    }

    /** Called by the refresh job with a fresh quote. Each triggered alert deactivates and notifies its owner. */
    @Transactional
    public int evaluate(Asset asset, Quote quote) {
        int fired = 0;
        for (PriceAlert a : alerts.findByAssetIdAndActiveTrue(asset.getId())) {
            if (!AlertRules.isTriggered(a.getCondition(), quote.price(), quote.previousClose(),
                    a.getTargetPrice(), a.getThresholdPercent())) continue;
            a.trigger(clock.instant());
            notifications.notify(a.getUserId(), NotificationType.PRICE_ALERT,
                    "Price alert: " + asset.getSymbol(), describe(a, asset, quote));
            fired++;
        }
        return fired;
    }

    private static String describe(PriceAlert a, Asset asset, Quote q) {
        String note = q.simulated() ? " (simulated price)" : "";
        return switch (a.getCondition()) {
            case ABOVE -> asset.getSymbol() + " is " + q.price().stripTrailingZeros().toPlainString()
                    + ", at or above your target of " + a.getTargetPrice().stripTrailingZeros().toPlainString() + note;
            case BELOW -> asset.getSymbol() + " is " + q.price().stripTrailingZeros().toPlainString()
                    + ", at or below your target of " + a.getTargetPrice().stripTrailingZeros().toPlainString() + note;
            case PERCENT_MOVE -> asset.getSymbol() + " moved at least " + a.getThresholdPercent().stripTrailingZeros()
                    .toPlainString() + "% from its previous close; now " + q.price().stripTrailingZeros().toPlainString() + note;
        };
    }

    private static AlertResponse toResponse(PriceAlert a, String symbol) {
        return new AlertResponse(a.getId(), a.getAssetId(), symbol, a.getCondition(), a.getTargetPrice(),
                a.getThresholdPercent(), a.isActive(), a.getTriggeredAt(), a.getCreatedAt());
    }
}
