package com.portfolioiq.integration;

import com.portfolioiq.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The application only starts if Flyway applied every migration and Hibernate's schema validation passed. */
class MigrationsIT extends IntegrationTestBase {
    @Test void everyMigrationWasApplied() {
        Integer applied = jdbc.queryForObject("select count(*) from flyway_schema_history where success", Integer.class);
        assertEquals(4, applied);
    }

    @Test void rolesAreSeeded() {
        assertEquals(List.of("ADMINISTRATOR", "ANALYST", "INVESTOR"), jdbc.queryForList("select name from roles order by name", String.class));
    }

    /** A portfolio and an asset to hang rows on. Returns {portfolioId, assetId}. */
    private long[] portfolioAndAsset() throws Exception {
        String admin = adminToken();
        String owner = registerNew();
        return new long[]{createPortfolio(owner, "Constraints", "USD"), createAsset(admin, "CHK")};
    }

    @Test void theDatabaseRejectsNegativeQuantities() throws Exception {
        long[] ids = portfolioAndAsset();
        long portfolio = ids[0], asset = ids[1];
        assertThrows(DataAccessException.class, () -> jdbc.update(
                "insert into transactions (portfolio_id, asset_id, type, quantity, unit_price, fee, total_amount, executed_at) values (?,?,?,?,?,?,?,now())",
                portfolio, asset, "BUY", -1, 10, 0, 10));
    }

    @Test void aTradeWithoutAnAssetIsRejected() throws Exception {
        long portfolio = portfolioAndAsset()[0];
        assertThrows(DataAccessException.class, () -> jdbc.update(
                "insert into transactions (portfolio_id, type, quantity, unit_price, fee, total_amount, executed_at) values (?,?,?,?,?,?,now())",
                portfolio, "BUY", 1, 10, 0, 10));
    }

    @Test void transferLegsMustCarryATransferGroup() throws Exception {
        long[] ids = portfolioAndAsset();
        long portfolio = ids[0], asset = ids[1];
        assertThrows(DataAccessException.class, () -> jdbc.update(
                "insert into transactions (portfolio_id, asset_id, type, quantity, unit_price, fee, total_amount, executed_at) values (?,?,?,?,?,?,?,now())",
                portfolio, asset, "TRANSFER_IN", 1, 10, 0, 10));
        // ...and ordinary trades must not
        assertThrows(DataAccessException.class, () -> jdbc.update(
                "insert into transactions (portfolio_id, asset_id, type, quantity, unit_price, fee, total_amount, executed_at, transfer_group) values (?,?,?,?,?,?,?,now(),gen_random_uuid())",
                portfolio, asset, "BUY", 1, 10, 0, 10));
    }

    @Test void unknownTransactionTypesAreRejected() throws Exception {
        long[] ids = portfolioAndAsset();
        long portfolio = ids[0], asset = ids[1];
        assertThrows(DataAccessException.class, () -> jdbc.update(
                "insert into transactions (portfolio_id, asset_id, type, quantity, unit_price, fee, total_amount, executed_at) values (?,?,?,?,?,?,?,now())",
                portfolio, asset, "GIFT", 1, 10, 0, 10));
    }
}
