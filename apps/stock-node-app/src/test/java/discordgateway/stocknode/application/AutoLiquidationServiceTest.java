package discordgateway.stocknode.application;

import discordgateway.stocknode.cache.RankingCacheRepository;
import discordgateway.stocknode.persistence.entity.StockAccountEntity;
import discordgateway.stocknode.persistence.entity.StockPositionEntity;
import discordgateway.stocknode.persistence.repository.StockPositionRepository;
import discordgateway.stocknode.persistence.repository.TradeLedgerRepository;
import discordgateway.stocknode.quote.model.StockQuote;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AutoLiquidationServiceTest {

    @Mock
    private StockPositionRepository stockPositionRepository;

    @Mock
    private TradeLedgerRepository tradeLedgerRepository;

    @Mock
    private RankingCacheRepository rankingCacheRepository;

    @Mock
    private TransactionOperations transactionOperations;

    private AutoLiquidationService autoLiquidationService;
    private final Clock clock = Clock.fixed(Instant.parse("2026-05-01T00:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        when(transactionOperations.execute(any())).thenAnswer(invocation ->
                ((TransactionCallback<Object>) invocation.getArgument(0)).doInTransaction(null)
        );
        autoLiquidationService = new AutoLiquidationService(
                stockPositionRepository,
                tradeLedgerRepository,
                rankingCacheRepository,
                clock,
                transactionOperations
        );
    }

    @Test
    void doesNotLiquidateWhenIsolatedEquityIsPositive() {
        StockPositionEntity position = leveragedPosition(1L, 10L, "NVDA", "5", "100.00", "50.0000", "500.0000", 10);
        when(stockPositionRepository.findAllBySymbolOrderByIdAsc("NVDA")).thenReturn(List.of(position));
        when(stockPositionRepository.findById(1L)).thenReturn(Optional.of(position));

        LiquidationBatchResult result = autoLiquidationService.liquidateExhaustedPositions(
                new StockQuote("US", "NVDA", new BigDecimal("95.00"), clock.instant())
        );

        assertThat(result.scannedCount()).isEqualTo(1);
        assertThat(result.liquidatedCount()).isZero();
        assertThat(result.failureCount()).isZero();
        verify(stockPositionRepository, never()).delete(any());
        verify(tradeLedgerRepository, never()).save(any());
    }

    @Test
    void liquidatesWhenIsolatedEquityIsExactlyZero() {
        StockPositionEntity position = leveragedPosition(1L, 10L, "NVDA", "5", "100.00", "50.0000", "500.0000", 10);
        when(stockPositionRepository.findAllBySymbolOrderByIdAsc("NVDA")).thenReturn(List.of(position));
        when(stockPositionRepository.findById(1L)).thenReturn(Optional.of(position));

        LiquidationBatchResult result = autoLiquidationService.liquidateExhaustedPositions(
                new StockQuote("US", "NVDA", new BigDecimal("90.00"), clock.instant())
        );

        assertThat(result.liquidatedCount()).isEqualTo(1);
        verify(stockPositionRepository).delete(position);
        verify(tradeLedgerRepository).save(any());
        verify(rankingCacheRepository).evictGuild(1001L, "2026-05");
    }

    @Test
    void liquidatesWhenIsolatedEquityFallsBelowZero() {
        StockPositionEntity position = leveragedPosition(1L, 10L, "NVDA", "5", "100.00", "50.0000", "500.0000", 10);
        when(stockPositionRepository.findAllBySymbolOrderByIdAsc("NVDA")).thenReturn(List.of(position));
        when(stockPositionRepository.findById(1L)).thenReturn(Optional.of(position));

        LiquidationBatchResult result = autoLiquidationService.liquidateExhaustedPositions(
                new StockQuote("US", "NVDA", new BigDecimal("85.00"), clock.instant())
        );

        assertThat(result.liquidatedCount()).isEqualTo(1);
        assertThat(result.failureCount()).isZero();
    }

    private StockPositionEntity leveragedPosition(
            Long positionId,
            Long accountId,
            String symbol,
            String quantity,
            String averageCost,
            String marginAmount,
            String notionalAmount,
            int leverage
    ) {
        StockAccountEntity account = StockAccountEntity.create(1001L, 2002L, "2026-05");
        ReflectionTestUtils.setField(account, "id", accountId);
        account.updateCashBalance(new BigDecimal("9900.0000"));

        StockPositionEntity position = StockPositionEntity.create(account, symbol, leverage);
        position.applyBuy(
                new BigDecimal(quantity).setScale(8),
                new BigDecimal(averageCost),
                new BigDecimal(marginAmount),
                new BigDecimal(notionalAmount),
                leverage
        );
        ReflectionTestUtils.setField(position, "id", positionId);
        return position;
    }
}
