package com.portfolioiq.transaction;

import com.portfolioiq.asset.Asset;
import com.portfolioiq.asset.AssetRepository;
import com.portfolioiq.asset.AssetType;
import com.portfolioiq.audit.AuditService;
import com.portfolioiq.common.ApiException;
import com.portfolioiq.finance.InsufficientHoldingsException;
import com.portfolioiq.finance.TransactionType;
import com.portfolioiq.notification.NotificationService;
import com.portfolioiq.portfolio.Portfolio;
import com.portfolioiq.portfolio.PortfolioRepository;
import com.portfolioiq.transaction.TransferDtos.TransferRequest;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TransferServiceTest {
    private static final long USER = 7L, FROM = 1L, TO = 2L, ASSET = 5L;
    private static final Instant NOW = Instant.parse("2026-06-01T00:00:00Z");
    private static final Instant WHEN = Instant.parse("2026-05-01T00:00:00Z");

    private final PortfolioRepository portfolios = mock(PortfolioRepository.class);
    private final AssetRepository assets = mock(AssetRepository.class);
    private final TransactionRepository txs = mock(TransactionRepository.class);
    private final AuditService audit = mock(AuditService.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private TransferService service;

    private static BigDecimal d(String v) { return new BigDecimal(v); }

    @BeforeEach
    void setUp() {
        service = new TransferService(portfolios, assets, txs, audit, notifications, Clock.fixed(NOW, ZoneOffset.UTC));
        when(portfolios.lockByIdAndUserId(FROM, USER)).thenReturn(Optional.of(new Portfolio(USER, "A", null, "USD", null)));
        when(portfolios.lockByIdAndUserId(TO, USER)).thenReturn(Optional.of(new Portfolio(USER, "B", null, "USD", null)));
        when(assets.findById(ASSET)).thenReturn(Optional.of(new Asset("ACME", "Acme", AssetType.STOCK, null, "USD")));
        when(txs.save(any(Transaction.class))).thenAnswer(i -> i.getArgument(0));
    }

    private Transaction tx(Long id, Long portfolio, TransactionType type, String qty, String price, Instant when) {
        Transaction t = new Transaction(portfolio, ASSET, type, d(qty), d(price), BigDecimal.ZERO, d("0"), when, null);
        ReflectionTestUtils.setField(t, "id", id);
        return t;
    }

    private TransferRequest request(String qty) { return new TransferRequest(FROM, TO, ASSET, d(qty), WHEN, null); }

    @Test
    void transferCarriesCostBasisWithoutRealizingAnything() {
        when(txs.findByPortfolioIdAndAssetIdAndTypeIn(eq(FROM), eq(ASSET), any()))
                .thenReturn(List.of(tx(10L, FROM, TransactionType.BUY, "10", "100", Instant.parse("2026-04-01T00:00:00Z"))));
        var response = service.transfer(USER, request("4"));
        var captor = ArgumentCaptor.forClass(Transaction.class);
        verify(txs, times(2)).save(captor.capture());
        var legs = captor.getAllValues();
        assertEquals(TransactionType.TRANSFER_OUT, legs.get(0).getType());
        assertEquals(TransactionType.TRANSFER_IN, legs.get(1).getType());
        assertEquals(legs.get(0).getTransferGroup(), legs.get(1).getTransferGroup());
        assertEquals(0, d("100").compareTo(response.unitCost()));
        assertEquals(0, d("400").compareTo(response.totalCost()));
    }

    @Test
    void cannotTransferMoreThanHeld() {
        when(txs.findByPortfolioIdAndAssetIdAndTypeIn(eq(FROM), eq(ASSET), any()))
                .thenReturn(List.of(tx(10L, FROM, TransactionType.BUY, "3", "100", Instant.parse("2026-04-01T00:00:00Z"))));
        assertThrows(InsufficientHoldingsException.class, () -> service.transfer(USER, request("4")));
        verify(txs, never()).save(any());
    }

    @Test
    void samePortfolioIsRejected() {
        var ex = assertThrows(ApiException.class,
                () -> service.transfer(USER, new TransferRequest(FROM, FROM, ASSET, d("1"), WHEN, null)));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    void differentBaseCurrenciesAreRejected() {
        when(portfolios.lockByIdAndUserId(TO, USER)).thenReturn(Optional.of(new Portfolio(USER, "B", null, "EUR", null)));
        var ex = assertThrows(ApiException.class, () -> service.transfer(USER, request("1")));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    void someoneElsesPortfolioLooksLikeNotFound() {
        when(portfolios.lockByIdAndUserId(TO, USER)).thenReturn(Optional.empty());
        var ex = assertThrows(ApiException.class, () -> service.transfer(USER, request("1")));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    void undoIsRefusedWhenDestinationSalesDependOnTheShares() {
        UUID group = UUID.randomUUID();
        Transaction out = new Transaction(FROM, ASSET, TransactionType.TRANSFER_OUT, d("4"), d("100"), BigDecimal.ZERO, d("400"), WHEN, null, group);
        Transaction in = new Transaction(TO, ASSET, TransactionType.TRANSFER_IN, d("4"), d("100"), BigDecimal.ZERO, d("400"), WHEN, null, group);
        ReflectionTestUtils.setField(out, "id", 30L);
        ReflectionTestUtils.setField(in, "id", 31L);
        Transaction laterSale = tx(32L, TO, TransactionType.SELL, "4", "120", Instant.parse("2026-05-10T00:00:00Z"));
        when(txs.findByTransferGroup(group)).thenReturn(List.of(out, in));
        when(txs.findByPortfolioIdAndAssetIdAndTypeIn(eq(TO), eq(ASSET), any())).thenReturn(List.of(in, laterSale));
        var ex = assertThrows(ApiException.class, () -> service.undo(USER, group));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        verify(txs, never()).delete(any(Transaction.class));
    }
}
