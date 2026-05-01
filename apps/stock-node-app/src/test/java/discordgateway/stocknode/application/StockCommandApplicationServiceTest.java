package discordgateway.stocknode.application;

import discordgateway.stock.command.StockCommand;
import discordgateway.stock.command.StockCommandEnvelope;
import discordgateway.stock.event.StockCommandResultEvent;
import discordgateway.stocknode.bootstrap.StockQuoteProperties;
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

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockCommandApplicationServiceTest {

    @Mock
    private QuoteService quoteService;

    @Mock
    private TradeExecutionService tradeExecutionService;

    @Mock
    private BalanceQueryService balanceQueryService;

    @Mock
    private PortfolioQueryService portfolioQueryService;

    @Mock
    private TradeHistoryQueryService tradeHistoryQueryService;

    @Mock
    private StockListQueryService stockListQueryService;

    @Mock
    private StockWatchlistService stockWatchlistService;

    @Mock
    private RankingService rankingService;

    @Mock
    private StockResponseFormatter stockResponseFormatter;

    private StockCommandApplicationService stockCommandApplicationService;

    @BeforeEach
    void setUp() {
        StockQuoteProperties stockQuoteProperties = new StockQuoteProperties();
        stockQuoteProperties.setDefaultMarket("us");
        stockCommandApplicationService = new StockCommandApplicationService(
                quoteService,
                tradeExecutionService,
                balanceQueryService,
                portfolioQueryService,
                tradeHistoryQueryService,
                stockListQueryService,
                stockWatchlistService,
                rankingService,
                stockResponseFormatter,
                stockQuoteProperties,
                Clock.fixed(Instant.parse("2026-04-30T01:00:00Z"), ZoneOffset.UTC),
                "stock-node-1"
        );
    }

    @Test
    void dispatchesQuoteCommand() {
        StockCommandEnvelope envelope = new StockCommandEnvelope(
                "cmd-1",
                1,
                1_234L,
                "gateway",
                new StockCommand.Quote(1001L, 2002L, List.of("AAPL")),
                "gateway-1"
        );
        StockQuoteResult stockQuoteResult = new StockQuoteResult(
                new StockQuote("us", "AAPL", new BigDecimal("123.45"), Instant.parse("2026-04-30T01:00:00Z")),
                QuoteSource.CACHE_FRESH,
                true
        );
        when(stockWatchlistService.validateTradable("us", "AAPL")).thenReturn(null);
        when(quoteService.getQuote("us", "AAPL", QuoteUsage.QUERY)).thenReturn(stockQuoteResult);
        when(stockResponseFormatter.formatQuote("us", "AAPL", stockQuoteResult)).thenReturn("quote message");

        StockCommandResultEvent event = stockCommandApplicationService.handle(envelope);

        assertThat(event.success()).isTrue();
        assertThat(event.resultType()).isEqualTo("QUOTE");
        assertThat(event.message()).isEqualTo("quote message");
        verify(quoteService).getQuote("us", "AAPL", QuoteUsage.QUERY);
    }

    @Test
    void dispatchesMultiQuoteCommandAsTable() {
        StockCommandEnvelope envelope = new StockCommandEnvelope(
                "cmd-1b",
                1,
                1_234L,
                "gateway",
                new StockCommand.Quote(1001L, 2002L, List.of("AAPL", "MSFT")),
                "gateway-1"
        );
        StockQuoteResult aaplQuote = new StockQuoteResult(
                new StockQuote("us", "AAPL", new BigDecimal("123.45"), Instant.parse("2026-04-30T01:00:00Z")),
                QuoteSource.CACHE_FRESH,
                true
        );
        StockQuoteResult msftQuote = new StockQuoteResult(
                new StockQuote("us", "MSFT", new BigDecimal("456.78"), Instant.parse("2026-04-30T01:00:01Z")),
                QuoteSource.PROVIDER_REFRESH,
                true
        );
        when(stockWatchlistService.validateTradable("us", "AAPL")).thenReturn(null);
        when(stockWatchlistService.validateTradable("us", "MSFT")).thenReturn(null);
        when(quoteService.getQuote("us", "AAPL", QuoteUsage.QUERY)).thenReturn(aaplQuote);
        when(quoteService.getQuote("us", "MSFT", QuoteUsage.QUERY)).thenReturn(msftQuote);
        when(stockResponseFormatter.formatQuoteTable("us", List.of("AAPL", "MSFT"), List.of(aaplQuote, msftQuote)))
                .thenReturn("quote table");

        StockCommandResultEvent event = stockCommandApplicationService.handle(envelope);

        assertThat(event.success()).isTrue();
        assertThat(event.resultType()).isEqualTo("QUOTE");
        assertThat(event.message()).isEqualTo("quote table");
        verify(stockWatchlistService).validateTradable("us", "AAPL");
        verify(stockWatchlistService).validateTradable("us", "MSFT");
        verify(quoteService).getQuote("us", "AAPL", QuoteUsage.QUERY);
        verify(quoteService).getQuote("us", "MSFT", QuoteUsage.QUERY);
    }

    @Test
    void dispatchesListCommand() {
        StockCommandEnvelope envelope = new StockCommandEnvelope(
                "cmd-list",
                1,
                1_234L,
                "gateway",
                new StockCommand.ListQuotes(1001L, 2002L),
                "gateway-1"
        );
        StockListView stockListView = new StockListView("US", List.of());
        when(stockListQueryService.getUsTopList()).thenReturn(stockListView);
        when(stockResponseFormatter.formatWatchlist(stockListView)).thenReturn("list message");

        StockCommandResultEvent event = stockCommandApplicationService.handle(envelope);

        assertThat(event.success()).isTrue();
        assertThat(event.resultType()).isEqualTo("LIST");
        assertThat(event.message()).isEqualTo("list message");
    }

    @Test
    void dispatchesBuyCommandWithQuantityContract() {
        StockCommandEnvelope envelope = new StockCommandEnvelope(
                "cmd-2",
                1,
                1_234L,
                "gateway",
                new StockCommand.Buy(1001L, 2002L, "AAPL", new BigDecimal("5"), 5),
                "gateway-1"
        );
        TradeExecutionResult tradeExecutionResult = new TradeExecutionResult(
                1L,
                1001L,
                2002L,
                TradeSide.BUY,
                "us",
                "AAPL",
                new BigDecimal("5.00000000"),
                5,
                new BigDecimal("1000.0000"),
                new BigDecimal("5000.0000"),
                new BigDecimal("5.00000000"),
                new BigDecimal("200.0000"),
                new BigDecimal("1000.0000"),
                new BigDecimal("9000.0000"),
                new BigDecimal("5.00000000"),
                new BigDecimal("200.0000"),
                null
        );
        when(tradeExecutionService.buy(1001L, 2002L, "AAPL", new BigDecimal("5"), 5))
                .thenReturn(tradeExecutionResult);
        when(stockResponseFormatter.formatTrade(tradeExecutionResult)).thenReturn("buy message");

        StockCommandResultEvent event = stockCommandApplicationService.handle(envelope);

        assertThat(event.success()).isTrue();
        assertThat(event.resultType()).isEqualTo("BUY");
        verify(tradeExecutionService).buy(1001L, 2002L, "AAPL", new BigDecimal("5"), 5);
    }

    @Test
    void dispatchesRankCommand() {
        StockCommandEnvelope envelope = new StockCommandEnvelope(
                "cmd-3",
                1,
                1_234L,
                "gateway",
                new StockCommand.Rank(1001L, 2002L, "day"),
                "gateway-1"
        );
        RankingView rankingView = new RankingView(
                1001L,
                "2026-05",
                "day",
                Instant.parse("2026-04-30T01:00:00Z"),
                List.of(new RankingEntryView(
                        1L,
                        2002L,
                        new BigDecimal("10100.0000"),
                        new BigDecimal("10000.0000"),
                        new BigDecimal("1.0000")
                ))
        );
        when(rankingService.getRanking(1001L, "day")).thenReturn(rankingView);
        when(stockResponseFormatter.formatRanking(rankingView)).thenReturn("rank message");

        StockCommandResultEvent event = stockCommandApplicationService.handle(envelope);

        assertThat(event.success()).isTrue();
        assertThat(event.resultType()).isEqualTo("RANK");
        assertThat(event.message()).isEqualTo("rank message");
    }
}
