package com.portfolioiq.transaction;

import com.portfolioiq.asset.Asset;
import com.portfolioiq.asset.AssetRepository;
import com.portfolioiq.audit.AuditService;
import com.portfolioiq.common.ApiException;
import com.portfolioiq.common.PageResponse;
import com.portfolioiq.finance.*;
import com.portfolioiq.notification.NotificationService;
import com.portfolioiq.notification.NotificationType;
import com.portfolioiq.portfolio.Portfolio;
import com.portfolioiq.portfolio.PortfolioRepository;
import com.portfolioiq.transaction.TransactionDtos.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TransactionService {
    private static final Set<String> SORTABLE = Set.of("executedAt", "totalAmount", "createdAt", "type", "id");

    private final PortfolioRepository portfolios;
    private final AssetRepository assets;
    private final TransactionRepository txs;
    private final DividendRepository dividends;
    private final AuditService audit;
    private final NotificationService notifications;
    private final Clock clock;

    public TransactionService(PortfolioRepository portfolios, AssetRepository assets, TransactionRepository txs,
                              DividendRepository dividends, AuditService audit, NotificationService notifications,
                              Clock clock) {
        this.portfolios = portfolios;
        this.assets = assets;
        this.txs = txs;
        this.dividends = dividends;
        this.audit = audit;
        this.notifications = notifications;
        this.clock = clock;
    }

    /**
     * Records a transaction. The portfolio row is locked first so concurrent requests cannot both pass the
     * holdings check. For asset transactions the full history is replayed with the new event included, which
     * also rejects back-dated sales that would have exceeded holdings at the time.
     */
    @Transactional
    public TransactionResponse record(Long userId, Long portfolioId, CreateTransactionRequest req) {
        Portfolio portfolio = portfolios.lockByIdAndUserId(portfolioId, userId).orElseThrow(TransactionService::portfolioNotFound);

        if (req.executedAt().isAfter(clock.instant().plus(Duration.ofMinutes(5)))) {
            throw badRequest("Transaction date cannot be in the future");
        }
        TransactionRules.Computed c;
        try {
            c = TransactionRules.compute(req.type(), req.quantity(), req.unitPrice(), req.fee(), req.amount());
        } catch (IllegalArgumentException e) {
            throw badRequest(e.getMessage());
        }

        Asset asset = null;
        if (TransactionRules.requiresAsset(req.type())) {
            if (req.assetId() == null) throw badRequest("assetId is required for " + req.type());
            asset = assets.findById(req.assetId()).orElseThrow(() -> badRequest("Unknown asset"));
            List<TradeEvent> events = new ArrayList<>();
            for (Transaction t : loadAssetTransactions(portfolioId, req.assetId())) events.add(TradeEvents.from(t));
            events.add(new TradeEvent(TradeEvent.Type.valueOf(req.type().name()), c.quantity(), c.unitPrice(),
                    c.fee(), req.executedAt()));
            PositionCalculator.calculate(events); // throws InsufficientHoldingsException (HTTP 422)
        } else if (req.assetId() != null) {
            throw badRequest("assetId must not be set for " + req.type());
        }

        Transaction saved = txs.save(new Transaction(portfolioId, req.assetId(), req.type(), c.quantity(),
                c.unitPrice(), c.fee(), c.total(), req.executedAt(), blankToNull(req.notes())));

        if (req.type() == TransactionType.DIVIDEND) {
            dividends.save(new Dividend(saved.getId(), portfolioId, req.assetId(), c.total(), null,
                    req.executedAt().atZone(ZoneOffset.UTC).toLocalDate()));
        }
        audit.log(userId, "TRANSACTION_CREATED", "TRANSACTION", String.valueOf(saved.getId()),
                req.type() + " in portfolio " + portfolioId);
        notifyRecorded(userId, req.type(), c, asset, portfolio.getName());
        return toResponse(saved, asset == null ? null : asset.getSymbol());
    }

    @Transactional(readOnly = true)
    public PageResponse<TransactionResponse> list(Long userId, Long portfolioId, TransactionType type, Long assetId,
                                                  Instant from, Instant to, Pageable pageable) {
        portfolios.findByIdAndUserId(portfolioId, userId).orElseThrow(TransactionService::portfolioNotFound);

        Specification<Transaction> spec = (root, q, cb) -> cb.equal(root.get("portfolioId"), portfolioId);
        if (type != null) spec = spec.and((root, q, cb) -> cb.equal(root.get("type"), type));
        if (assetId != null) spec = spec.and((root, q, cb) -> cb.equal(root.get("assetId"), assetId));
        if (from != null) spec = spec.and((root, q, cb) -> cb.greaterThanOrEqualTo(root.get("executedAt"), from));
        if (to != null) spec = spec.and((root, q, cb) -> cb.lessThanOrEqualTo(root.get("executedAt"), to));

        var page = txs.findAll(spec, safePageable(pageable));
        Map<Long, String> symbols = symbolsFor(page.getContent());
        return PageResponse.of(page.map(t -> toResponse(t, symbols.get(t.getAssetId()))));
    }

    /** Refuses a delete that would leave a later sale without enough holdings. */
    @Transactional
    public void delete(Long userId, Long portfolioId, Long transactionId) {
        portfolios.lockByIdAndUserId(portfolioId, userId).orElseThrow(TransactionService::portfolioNotFound);
        Transaction target = txs.findByIdAndPortfolioId(transactionId, portfolioId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Transaction not found"));

        if (target.getTransferGroup() != null) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "This is part of a transfer. Undo it with DELETE /api/transfers/" + target.getTransferGroup());
        }
        if (TradeEvents.ASSET_TYPES.contains(target.getType())) {
            List<TradeEvent> remaining = loadAssetTransactions(portfolioId, target.getAssetId()).stream()
                    .filter(t -> !Objects.equals(t.getId(), target.getId()))
                    .map(TradeEvents::from).toList();
            try {
                PositionCalculator.calculate(remaining);
            } catch (InsufficientHoldingsException e) {
                throw new ApiException(HttpStatus.CONFLICT,
                        "Deleting this transaction would leave later sales without enough holdings");
            }
        }
        txs.delete(target);
        audit.log(userId, "TRANSACTION_DELETED", "TRANSACTION", String.valueOf(transactionId),
                target.getType() + " in portfolio " + portfolioId);
    }

    private void notifyRecorded(Long userId, TransactionType type, TransactionRules.Computed c, Asset asset, String portfolioName) {
        if (type == TransactionType.DIVIDEND) {
            notifications.notify(userId, NotificationType.DIVIDEND_RECEIVED, "Dividend received: " + asset.getSymbol(),
                    plain(c.total()) + " credited to " + portfolioName);
            return;
        }
        String what = asset != null ? plain(c.quantity()) + " " + asset.getSymbol() : plain(c.total());
        notifications.notify(userId, NotificationType.TRANSACTION_RECORDED, type + " recorded", what + " in " + portfolioName);
    }

    private static String plain(java.math.BigDecimal v) { return v.stripTrailingZeros().toPlainString(); }

    private List<Transaction> loadAssetTransactions(Long portfolioId, Long assetId) {
        return txs.findByPortfolioIdAndAssetIdAndTypeIn(portfolioId, assetId, TradeEvents.ASSET_TYPES);
    }

    private Map<Long, String> symbolsFor(List<Transaction> rows) {
        Set<Long> ids = rows.stream().map(Transaction::getAssetId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) return Collections.emptyMap(); // Map.of().get(null) would throw
        return assets.findAllById(ids).stream().collect(Collectors.toMap(Asset::getId, Asset::getSymbol));
    }

    /** Whitelists sortable fields and caps page size so clients cannot trigger arbitrary sorts or huge pages. */
    private static Pageable safePageable(Pageable p) {
        Sort sort = Sort.unsorted();
        for (Sort.Order o : p.getSort()) {
            if (!SORTABLE.contains(o.getProperty())) throw badRequest("Cannot sort by " + o.getProperty());
            sort = sort.and(Sort.by(o));
        }
        if (sort.isUnsorted()) sort = Sort.by(Sort.Direction.DESC, "executedAt");
        sort = sort.and(Sort.by(Sort.Direction.DESC, "id"));
        return PageRequest.of(Math.max(p.getPageNumber(), 0), Math.min(Math.max(p.getPageSize(), 1), 100), sort);
    }

    private static TransactionResponse toResponse(Transaction t, String symbol) {
        return new TransactionResponse(t.getId(), t.getPortfolioId(), t.getAssetId(), symbol, t.getType(),
                t.getQuantity(), t.getUnitPrice(), t.getFee(), t.getTotalAmount(), t.getExecutedAt(), t.getNotes(),
                t.getTransferGroup());
    }

    private static ApiException portfolioNotFound() { return new ApiException(HttpStatus.NOT_FOUND, "Portfolio not found"); }
    private static ApiException badRequest(String m) { return new ApiException(HttpStatus.BAD_REQUEST, m); }
    private static String blankToNull(String s) { return s == null || s.isBlank() ? null : s.trim(); }
}
