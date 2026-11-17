package com.portfolioiq.asset;

import com.portfolioiq.asset.AssetDtos.*;
import com.portfolioiq.audit.AuditService;
import com.portfolioiq.common.ApiException;
import com.portfolioiq.common.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class AssetService {
    private final AssetRepository assets;
    private final AuditService audit;

    public AssetService(AssetRepository assets, AuditService audit) {
        this.assets = assets;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public PageResponse<AssetResponse> search(String q, int page, int size) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by("symbol"));
        var result = (q == null || q.isBlank())
                ? assets.findAll(pageable)
                : assets.findBySymbolContainingIgnoreCaseOrNameContainingIgnoreCase(q.trim(), q.trim(), pageable);
        return PageResponse.of(result.map(AssetResponse::of));
    }

    @Transactional(readOnly = true)
    public AssetResponse get(Long id) {
        return AssetResponse.of(assets.findById(id).orElseThrow(
                () -> new ApiException(HttpStatus.NOT_FOUND, "Asset not found")));
    }

    @Transactional
    public AssetResponse create(Long userId, CreateAssetRequest req) {
        String symbol = req.symbol().trim().toUpperCase(Locale.ROOT);
        if (assets.existsBySymbolIgnoreCase(symbol)) {
            throw new ApiException(HttpStatus.CONFLICT, "An asset with this symbol already exists");
        }
        String currency = req.currency() == null ? "USD" : req.currency().toUpperCase(Locale.ROOT);
        Asset saved = assets.save(new Asset(symbol, req.name().trim(), req.assetType(),
                req.exchange() == null ? null : req.exchange().trim(), currency));
        audit.log(userId, "ASSET_CREATED", "ASSET", String.valueOf(saved.getId()), symbol);
        return AssetResponse.of(saved);
    }
}
