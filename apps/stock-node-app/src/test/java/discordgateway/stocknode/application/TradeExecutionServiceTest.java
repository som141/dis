package discordgateway.stocknode.application;

import discordgateway.stocknode.bootstrap.StockQuoteProperties;
import discordgateway.stocknode.cache.RankingCacheRepository;
import discordgateway.stocknode.persistence.entity.StockAccountEntity;
import discordgateway.stocknode.persistence.entity.StockPositionEntity;
import discordgateway.stocknode.persistence.repository.StockAccountRepository;
import discordgateway.stocknode.persistence.repository.StockPositionRepository;
import discordgateway.stocknode.persistence.repository.TradeLedgerRepository;
import discordgateway.stocknode.quote.model.StockQuote;
import discordgateway.stocknode.quote.service.QuoteService;
import discordgateway.stocknode.quote.service.QuoteSource;
import discordgateway.stocknode.quote.service.QuoteUsage;
import discordgateway.stocknode.quote.service.StockQuoteResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TradeExecutionServiceTest {

    @Mock
    private DailyAllowanceService dailyAllowanceService;

    @Mock
    private StockAccountRepository stockAccountRepository;

    @Mock
    private StockPositionRepository stockPositionRepository;

    @Mock
    private TradeLedgerRepository tradeLedgerRepository;

    @Mock
    private QuoteService quoteService;

    @Mock
    private StockWatchlistService stockWatchlistService;

    @Mock
    private RankingCacheRepository rankingCacheRepository;

    private TradeExecutionService tradeExecutionService;
    private final StockQuoteProperties stockQuoteProperties = new StockQuoteProperties();
    private final Clock clock = Clock.fixed(Instant.parse("2026-04-30T01:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        stockQuoteProperties.setDefaultMarket("us");
        tradeExecutionService = new TradeExecutionService(
                dailyAllowanceService,
                stockWatchlistService,
                stockAccountRepository,
                stockPositionRepository,
                tradeLedgerRepository,
                quoteService,
                rankingCacheRepository,
                stockQuoteProperties,
                clock
        );
    }

    @Test
    void buyUsesAmountContractAndUpdatesBalanceAndPosition() {
        StockAccountEntity account = account(10L, "10000.0000");
        when(stockWatchlistService.validateTradable("us", "aapl")).thenReturn(null);
        when(dailyAllowanceService.ensureSettledAccount(1001L, 2002L)).thenReturn(account);
        when(quoteService.getQuote("us", "aapl", QuoteUsage.TRADE)).thenReturn(
                freshQuote("us", "AAPL", "200.00")
        );
        when(stockPositionRepository.findByAccountIdAndSymbol(10L, "AAPL")).thenReturn(Optional.empty());

        TradeExecutionResult result = tradeExecutionService.buy(1001L, 2002L, "aapl", new BigDecimal("1000.00"), 1);

        assertThat(result.side()).isEqualTo(TradeSide.BUY);
        assertThat(result.executedQuantity()).isEqualByComparingTo("5.00000000");
        assertThat(result.remainingCash()).isEqualByComparingTo("9000.0000");
        assertThat(result.remainingPositionQuantity()).isEqualByComparingTo("5.00000000");
        assertThat(result.remainingPositionAverageCost()).isEqualByComparingTo("200.0000");
        verify(stockPositionRepository).save(any(StockPositionEntity.class));
        verify(tradeLedgerRepository).save(any());
        verify(rankingCacheRepository).evictGuild(1001L, "legacy");
    }

    @Test
    void sellRemovesPositionAndRestoresCash() {
        StockAccountEntity account = account(10L, "9000.0000");
        StockPositionEntity position = StockPositionEntity.create(account, "AAPL");
        position.applyBuy(new BigDecimal("5.00000000"), new BigDecimal("200.00"));
        when(stockWatchlistService.validateTradable("us", "AAPL")).thenReturn(null);
        when(dailyAllowanceService.ensureSettledAccount(1001L, 2002L)).thenReturn(account);
        when(stockPositionRepository.findByAccountIdAndSymbol(10L, "AAPL")).thenReturn(Optional.of(position));
        when(quoteService.getQuote("us", "AAPL", QuoteUsage.TRADE)).thenReturn(
                freshQuote("us", "AAPL", "210.00")
        );

        TradeExecutionResult result = tradeExecutionService.sell(1001L, 2002L, "AAPL", new BigDecimal("5.00000000"));

        assertThat(result.side()).isEqualTo(TradeSide.SELL);
        assertThat(result.remainingCash()).isEqualByComparingTo("10050.0000");
        assertThat(result.remainingPositionQuantity()).isEqualByComparingTo("0.00000000");
        verify(stockPositionRepository).delete(position);
        verify(tradeLedgerRepository).save(any());
        verify(rankingCacheRepository).evictGuild(1001L, "legacy");
    }

    @Test
    void rejectsStaleTradeQuote() {
        StockAccountEntity account = account(10L, "10000.0000");
        StockPositionEntity position = StockPositionEntity.create(account, "AAPL");
        position.applyBuy(new BigDecimal("1.00000000"), new BigDecimal("200.00"));
        when(stockWatchlistService.validateTradable("us", "AAPL")).thenReturn(null);
        when(dailyAllowanceService.ensureSettledAccount(1001L, 2002L)).thenReturn(account);
        when(quoteService.getQuote("us", "AAPL", QuoteUsage.TRADE)).thenReturn(
                new StockQuoteResult(
                        new StockQuote("us", "AAPL", new BigDecimal("200.00"), Instant.parse("2026-04-30T00:00:00Z")),
                        QuoteSource.CACHE_STALE,
                        false
                )
        );
        when(stockPositionRepository.findByAccountIdAndSymbol(10L, "AAPL")).thenReturn(Optional.of(position));

        assertThatThrownBy(() ->
                tradeExecutionService.sell(1001L, 2002L, "AAPL", new BigDecimal("1.00000000"))
        ).isInstanceOf(StaleQuoteException.class);
    }

    private StockQuoteResult freshQuote(String market, String symbol, String price) {
        return new StockQuoteResult(
                new StockQuote(market, symbol, new BigDecimal(price), clock.instant()),
                QuoteSource.PROVIDER_REFRESH,
                true
        );
    }

    private static StockAccountEntity account(Long id, String cashBalance) {
        StockAccountEntity account = StockAccountEntity.create(1001L, 2002L);
        ReflectionTestUtils.setField(account, "id", id);
        account.updateCashBalance(new BigDecimal(cashBalance));
        return account;
    }
}
