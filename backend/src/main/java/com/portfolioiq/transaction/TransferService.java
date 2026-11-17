package com.portfolioiq.transaction;

import com.portfolioiq.asset.Asset;
import com.portfolioiq.asset.AssetRepository;
import com.portfolioiq.audit.AuditService;
import com.portfolioiq.common.ApiException;
import com.portfolioiq.finance.*;
import com.portfolioiq.notification.NotificationService;
import com.portfolioiq.notification.NotificationType;
import com.portfolioiq.portfolio.Portfolio;
import com.portfolioiq.portfolio.PortfolioRepository;
import com.portfolioiq.transaction.TransferDtos.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.util.*;

/**
 * Moves shares between two portfolios owned by the same user without realizing profit or loss. The source
 * portfolio loses the shares and their cost basis (TRANSFER_OUT); the destination receives them with the same
 * cost per unit (TRANSFER_IN). Both legs share a transfer id so they can be undone together.
 */
@Service
public class TransferService {
    private final PortfolioRepository portfolios;
    private final AssetRepository assets;
    private final TransactionRepository txs;
    private final AuditService audit;
    private final NotificationService notifications;
    private final Clock clock;

    public TransferService(PortfolioRepository portfolios, AssetRepository assets, TransactionRepository txs,
                           AuditService audit, NotificationService notifications, Clock clock) {
        this.portfolios = portfolios;
        this.assets = assets;
        this.txs = txs;
        this.audit = audit;
        this.notifications = notifications;
        this.clock = clock;
    }

    @Transactional
    public TransferResponse transfer(Long userId, TransferRequest r) {
        if (Objects.equals(r.fromPortfolioId(), r.toPortfolioId())) {
            throw bad("Source and destination portfolios must differ");
        }
        Map<Long, Portfolio> locked = lockBoth(userId, r.fromPortfolioId(), r.toPortfolioId());
        Portfolio from = locked.get(r.fromPortfolioId());
        Portfolio to = locked.get(r.toPortfolioId());
        if (!from.getBaseCurrency().equals(to.getBaseCurrency())) {
            throw bad("Both portfolios must use the same base currency (currency conversion is not supported)");
        }
        if (r.executedAt().isAfter(clock.instant().plus(Duration.ofMinutes(5)))) {
            throw bad("Transfer date cannot be in the future");
        }
        BigDecimal qty;
        try {
            qty = TransactionRules.validateQuantity(r.quantity());
        } catch (IllegalArgumentException e) {
            throw bad(e.getMessage());
        }
        Asset asset = assets.findById(r.assetId()).orElseThrow(() -> bad("Unknown asset"));

        List<TradeEvent> events = new ArrayList<>();
        for (Transaction t : txs.findByPortfolioIdAndAssetIdAndTypeIn(r.fromPortfolioId(), r.assetId(), TradeEvents.ASSET_TYPES)) {
            events.add(TradeEvents.from(t));
        }
        // Cost basis as it stood at the transfer date.
        Position before = PositionCalculator.calculate(
                events.stream().filter(e -> !e.executedAt().isAfter(r.executedAt())).toList());
        // Full replay with the new leg: rejects the transfer (422) if it would exceed holdings now or strand later sales.
        events.add(new TradeEvent(TradeEvent.Type.TRANSFER_OUT, qty, BigDecimal.ZERO, BigDecimal.ZERO, r.executedAt()));
        PositionCalculator.calculate(events);

        BigDecimal unitCost = before.unitCost();
        if (unitCost.signum() <= 0) throw bad("No cost basis is available to transfer");
        BigDecimal total = qty.multiply(unitCost).setScale(4, RoundingMode.HALF_UP);
        BigDecimal noFee = BigDecimal.ZERO.setScale(4);
        String notes = r.notes() == null || r.notes().isBlank() ? null : r.notes().trim();
        UUID group = UUID.randomUUID();

        txs.save(new Transaction(r.fromPortfolioId(), r.assetId(), TransactionType.TRANSFER_OUT, qty, unitCost, noFee,
                total, r.executedAt(), notes, group));
        txs.save(new Transaction(r.toPortfolioId(), r.assetId(), TransactionType.TRANSFER_IN, qty, unitCost, noFee,
                total, r.executedAt(), notes, group));

        audit.log(userId, "ASSET_TRANSFERRED", "TRANSFER", group.toString(),
                asset.getSymbol() + " from portfolio " + r.fromPortfolioId() + " to " + r.toPortfolioId());
        notifications.notify(userId, NotificationType.TRANSFER_COMPLETED, "Transfer completed",
                qty.stripTrailingZeros().toPlainString() + " " + asset.getSymbol() + " moved from "
                        + from.getName() + " to " + to.getName());
        return new TransferResponse(group, r.fromPortfolioId(), r.toPortfolioId(), r.assetId(), qty, unitCost, total, r.executedAt());
    }

    /** Removes both legs. Refused if later sales in the destination depend on the transferred shares. */
    @Transactional
    public void undo(Long userId, UUID transferId) {
        List<Transaction> legs = txs.findByTransferGroup(transferId);
        Transaction out = legs.stream().filter(t -> t.getType() == TransactionType.TRANSFER_OUT).findFirst()
                .orElseThrow(TransferService::notFound);
        Transaction in = legs.stream().filter(t -> t.getType() == TransactionType.TRANSFER_IN).findFirst()
                .orElseThrow(TransferService::notFound);
        lockBoth(userId, out.getPortfolioId(), in.getPortfolioId()); // 404 unless the user owns both portfolios

        List<TradeEvent> remaining = txs.findByPortfolioIdAndAssetIdAndTypeIn(in.getPortfolioId(), in.getAssetId(), TradeEvents.ASSET_TYPES)
                .stream().filter(t -> !Objects.equals(t.getId(), in.getId())).map(TradeEvents::from).toList();
        try {
            PositionCalculator.calculate(remaining);
        } catch (InsufficientHoldingsException e) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Cannot undo: later sales in the destination portfolio depend on these shares");
        }
        txs.delete(out);
        txs.delete(in);
        audit.log(userId, "TRANSFER_UNDONE", "TRANSFER", transferId.toString(), null);
    }

    /** Locks in ascending id order so two opposite transfers can never deadlock. */
    private Map<Long, Portfolio> lockBoth(Long userId, Long a, Long b) {
        Long first = Math.min(a, b), second = Math.max(a, b);
        Portfolio pFirst = portfolios.lockByIdAndUserId(first, userId).orElseThrow(TransferService::notFound);
        Portfolio pSecond = portfolios.lockByIdAndUserId(second, userId).orElseThrow(TransferService::notFound);
        return Map.of(first, pFirst, second, pSecond);
    }

    private static ApiException notFound() { return new ApiException(HttpStatus.NOT_FOUND, "Transfer or portfolio not found"); }
    private static ApiException bad(String m) { return new ApiException(HttpStatus.BAD_REQUEST, m); }
}
