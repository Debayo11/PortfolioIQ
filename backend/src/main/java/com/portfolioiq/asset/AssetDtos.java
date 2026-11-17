package com.portfolioiq.asset;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AssetDtos {
    private AssetDtos() {}

    public record CreateAssetRequest(
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9.\\-]{1,20}$", message = "must be 1-20 letters, digits, '.' or '-'") String symbol,
            @NotBlank @Size(max = 200) String name,
            @NotNull AssetType assetType,
            @Size(max = 30) String exchange,
            @Pattern(regexp = "^[A-Za-z]{3}$", message = "must be a 3-letter currency code") String currency) {}

    public record AssetResponse(Long id, String symbol, String name, AssetType assetType, String exchange, String currency) {
        static AssetResponse of(Asset a) {
            return new AssetResponse(a.getId(), a.getSymbol(), a.getName(), a.getAssetType(), a.getExchange(), a.getCurrency());
        }
    }
}
