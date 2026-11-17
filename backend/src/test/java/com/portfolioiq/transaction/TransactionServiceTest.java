package com.portfolioiq.transaction;

import com.portfolioiq.asset.Asset;
import com.portfolioiq.asset.AssetRepository;
import com.portfolioiq.asset.AssetType;
import com.portfolioiq.audit.AuditService;
import com.portfolioiq.common.ApiException;
import com.portfolioiq.finance.InsufficientHoldingsException;
import com.portfolioiq.finance.TransactionType;
import com.portfolioiq.portfolio.Portfolio;
import com.portfolioiq.portfolio.PortfolioRepository;
import com.portfolioiq.transaction.TransactionDtos.CreateTransactionRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TransactionServiceTest {
    private static final long USER = 7L, PORTFOLIO = 1L, ASSET = 5L;
    private static final Instant NOW = Instant.parse("2026-06-01T00:00:00Z");
    private static final Instant EARLIER = Instant.parse("2026-05-01T00:00:00Z");

    private final PortfolioRepository portfolios = mock(PortfolioRepository.class);
    private final AssetRepository assets = mock(AssetRepository.class);
    private final TransactionRepository txs = mock(TransactionRepository.class);
    private final DividendRepository dividends = mock(DividendRepository.class);
    private final AuditService audit = mock(AuditService.class);
    private TransactionService service;

    private static BigDecimal d(String v) { return new BigDecimal(v); }

    @BeforeEach
    void setUp() {
        service = new TransactionService(portfolios, assets, txs, dividends, audit, Clock.fixed(NOW, ZoneOffset.UTC));
        when(portfolios.lockByIdAndUserId(PORTFOLIO, USER))
                .thenReturn(Optional.of(new Portfolio(USER, "Main", null, "USD", null)));
        when(assets.findById(ASSET)).thenReturn(Optional.of(new Asset("ACME", "Acme Corp", AssetType.STOCK, null, "USD")));
        when(txs.save(any(Transaction.class))).thenAnswer(i -> i.getArgument(0));
    }

    private Transaction held(Long id, TransactionType type, String qty, String price, Instant when) {
        Transaction t = new Transaction(PORTFOLIO, ASSET, type, d(qty), d(price), BigDecimal.ZERO, d("0"), when, null);
        ReflectionTestUtils.setField(t, "id", id);
        return t;
    }

    private CreateTransactionRequest req(TransactionType type, Long assetId, String qty, String price, String fee, String amount) {
        return new CreateTransactionRequest(type, assetId, qty == null ? null : d(qty), price == null ? null : d(price),
                fee == null ? null : d(fee), amount == null ? null : d(amount), EARLIER, null);
    }

    @Test
    void buyStoresTotalIncludingFee() {
        when(txs.findByPortfolioIdAndAssetIdAndTypeIn(eq(PORTFOLIO), eq(ASSET), any())).thenReturn(List.of());
        service.record(USER, PORTFOLIO, req(TransactionType.BUY, ASSET, "10", "100", "1", null));
        var captor = ArgumentCaptor.forClass(Transaction.class);
        verify(txs).save(captor.capture());
        assertEquals(0, d("1001").compareTo(captor.getValue().getTotalAmount()));
        verify(audit).log(eq(USER), eq("TRANSACTION_CREATED"), any(), any(), any());
    }

    @Test
    void sellingMoreThanHeldIsRejectedAndNothingIsSaved() {
        when(txs.findByPortfolioIdAndAssetIdAndTypeIn(eq(PORTFOLIO), eq(ASSET), any()))
                .thenReturn(List.of(held(10L, TransactionType.BUY, "5", "10", Instant.parse("2026-04-01T00:00:00Z"))));
        assertThrows(InsufficientHoldingsException.class,
                () -> service.record(USER, PORTFOLIO, req(TransactionType.SELL, ASSET, "6", "10", null, null)));
        verify(txs, never()).save(any());
    }

    @Test
    void portfolioOwnedBySomeoneElseLooksLikeNotFound() {
        when(portfolios.lockByIdAndUserId(PORTFOLIO, 99L)).thenReturn(Optional.empty());
        var ex = assertThrows(ApiException.class,
                () -> service.record(99L, PORTFOLIO, req(TransactionType.DEPOSIT, null, null, null, null, "100")));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    void cashTransactionsRejectAnAsset() {
        var ex = assertThrows(ApiException.class,
                () -> service.record(USER, PORTFOLIO, req(TransactionType.DEPOSIT, ASSET, null, null, null, "100")));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    void futureDatedTransactionsAreRejected() {
        var future = new CreateTransactionRequest(TransactionType.DEPOSIT, null, null, null, null, d("10"),
                NOW.plusSeconds(86_400), null);
        assertEquals(HttpStatus.BAD_REQUEST,
                assertThrows(ApiException.class, () -> service.record(USER, PORTFOLIO, future)).getStatus());
    }

    @Test
    void dividendCreatesDividendRow() {
        when(txs.findByPortfolioIdAndAssetIdAndTypeIn(eq(PORTFOLIO), eq(ASSET), any()))
                .thenReturn(List.of(held(10L, TransactionType.BUY, "10", "10", Instant.parse("2026-04-01T00:00:00Z"))));
        service.record(USER, PORTFOLIO, req(TransactionType.DIVIDEND, ASSET, "10", "0.5", null, null));
        verify(dividends).save(any(Dividend.class));
    }

    @Test
    void deletingABuyThatBacksALaterSaleIsRefused() {
        Transaction buy = held(10L, TransactionType.BUY, "5", "10", Instant.parse("2026-04-01T00:00:00Z"));
        Transaction sell = held(11L, TransactionType.SELL, "5", "12", Instant.parse("2026-04-02T00:00:00Z"));
        when(txs.findByIdAndPortfolioId(10L, PORTFOLIO)).thenReturn(Optional.of(buy));
        when(txs.findByPortfolioIdAndAssetIdAndTypeIn(eq(PORTFOLIO), eq(ASSET), any())).thenReturn(List.of(buy, sell));
        var ex = assertThrows(ApiException.class, () -> service.delete(USER, PORTFOLIO, 10L));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        verify(txs, never()).delete(any(Transaction.class));
    }

    @Test
    void deletingAnUnrelatedTransactionSucceeds() {
        Transaction deposit = new Transaction(PORTFOLIO, null, TransactionType.DEPOSIT, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, d("100"), EARLIER, null);
        ReflectionTestUtils.setField(deposit, "id", 12L);
        when(txs.findByIdAndPortfolioId(12L, PORTFOLIO)).thenReturn(Optional.of(deposit));
        service.delete(USER, PORTFOLIO, 12L);
        verify(txs).delete(deposit);
    }
}
