package com.portfolioiq.watchlist;

import com.portfolioiq.asset.Asset;
import com.portfolioiq.asset.AssetRepository;
import com.portfolioiq.audit.AuditService;
import com.portfolioiq.common.ApiException;
import com.portfolioiq.market.MarketDataService;
import com.portfolioiq.market.MarketDtos.QuoteResponse;
import com.portfolioiq.market.QuoteView;
import com.portfolioiq.watchlist.WatchlistDtos.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class WatchlistService {
    private static final int MAX_LISTS = 20;
    private static final int MAX_ITEMS = 100;

    private final WatchlistRepository lists;
    private final AssetRepository assets;
    private final MarketDataService market;
    private final AuditService audit;

    public WatchlistService(WatchlistRepository lists, AssetRepository assets, MarketDataService market, AuditService audit) {
        this.lists = lists;
        this.assets = assets;
        this.market = market;
        this.audit = audit;
    }

    @Transactional
    public WatchlistResponse create(Long userId, WatchlistRequest req) {
        String name = req.name().trim();
        if (lists.countByUserId(userId) >= MAX_LISTS) throw new ApiException(HttpStatus.CONFLICT, "Watchlist limit reached");
        if (lists.existsByUserIdAndNameIgnoreCase(userId, name)) {
            throw new ApiException(HttpStatus.CONFLICT, "You already have a watchlist with this name");
        }
        Watchlist saved = lists.save(new Watchlist(userId, name));
        audit.log(userId, "WATCHLIST_CREATED", "WATCHLIST", String.valueOf(saved.getId()), name);
        return view(saved);
    }

    @Transactional(readOnly = true)
    public List<WatchlistResponse> list(Long userId) {
        return lists.findByUserIdOrderByNameAsc(userId).stream().map(this::view).toList();
    }

    @Transactional(readOnly = true)
    public WatchlistResponse get(Long userId, Long id) { return view(owned(userId, id)); }

    @Transactional
    public WatchlistResponse rename(Long userId, Long id, WatchlistRequest req) {
        Watchlist w = owned(userId, id);
        String name = req.name().trim();
        if (!w.getName().equalsIgnoreCase(name) && lists.existsByUserIdAndNameIgnoreCase(userId, name)) {
            throw new ApiException(HttpStatus.CONFLICT, "You already have a watchlist with this name");
        }
        w.rename(name);
        return view(w);
    }

    @Transactional
    public void delete(Long userId, Long id) {
        lists.delete(owned(userId, id));
        audit.log(userId, "WATCHLIST_DELETED", "WATCHLIST", String.valueOf(id), null);
    }

    @Transactional
    public WatchlistResponse addItem(Long userId, Long id, Long assetId) {
        Watchlist w = owned(userId, id);
        if (assets.findById(assetId).isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "Unknown asset");
        if (w.getAssetIds().size() >= MAX_ITEMS && !w.getAssetIds().contains(assetId)) {
            throw new ApiException(HttpStatus.CONFLICT, "Watchlist is full");
        }
        w.getAssetIds().add(assetId);
        return view(w);
    }

    @Transactional
    public WatchlistResponse removeItem(Long userId, Long id, Long assetId) {
        Watchlist w = owned(userId, id);
        w.getAssetIds().remove(assetId);
        return view(w);
    }

    private WatchlistResponse view(Watchlist w) {
        List<Asset> items = assets.findAllById(w.getAssetIds()).stream()
                .sorted(Comparator.comparing(Asset::getSymbol)).toList();
        Map<Long, QuoteView> quotes = market.latestMany(items);
        List<WatchlistItemResponse> out = items.stream().map(a -> {
            QuoteView q = quotes.get(a.getId());
            return new WatchlistItemResponse(a.getId(), a.getSymbol(), a.getName(),
                    q == null ? null : QuoteResponse.of(a, q));
        }).collect(Collectors.toList());
        return new WatchlistResponse(w.getId(), w.getName(), out);
    }

    private Watchlist owned(Long userId, Long id) {
        return lists.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Watchlist not found"));
    }
}
