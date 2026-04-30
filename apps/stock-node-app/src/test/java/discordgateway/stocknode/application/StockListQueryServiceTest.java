package discordgateway.stocknode.application;

import discordgateway.stocknode.bootstrap.StockMarketDataProperties;
import discordgateway.stocknode.persistence.entity.StockWatchlistEntity;
import discordgateway.stocknode.quote.model.StockQuote;
import discordgateway.stocknode.quote.service.QuoteService;
import discordgateway.stocknode.quote.service.QuoteSource;
import discordgateway.stocknode.quote.service.QuoteUsage;
import discordgateway.stocknode.quote.service.StockQuoteResult;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class StockListQueryServiceTest {

    @Test
    void buildsListWithReadyAndPendingQuotes() {
        StockWatchlistService stockWatchlistService = Mockito.mock(StockWatchlistService.class);
        QuoteService quoteService = Mockito.mock(QuoteService.class);
        StockMarketDataProperties stockMarketDataProperties = new StockMarketDataProperties();
        stockMarketDataProperties.setMarket("US");
        stockMarketDataProperties.setTopRankLimit(10);

        StockWatchlistEntity nvda = watchlist("US", "NVDA", "NVIDIA Corporation", 1);
        StockWatchlistEntity aapl = watchlist("US", "AAPL", "Apple Inc.", 2);
        when(stockWatchlistService.getEnabledByMarket("US", 10)).thenReturn(List.of(nvda, aapl));
        when(quoteService.findCachedQuote("US", "NVDA", QuoteUsage.QUERY)).thenReturn(Optional.of(
                new StockQuoteResult(
                        new StockQuote("US", "NVDA", new BigDecimal("216.61"), Instant.parse("2026-05-01T00:00:00Z"), "finnhub", null, new BigDecimal("4.00"), null, null, null, null),
                        QuoteSource.CACHE_FRESH,
                        true
                )
        ));
        when(quoteService.findCachedQuote("US", "AAPL", QuoteUsage.QUERY)).thenReturn(Optional.empty());

        StockListQueryService service = new StockListQueryService(
                stockWatchlistService,
                quoteService,
                stockMarketDataProperties
        );

        StockListView view = service.getUsTopList();

        assertThat(view.items()).hasSize(2);
        assertThat(view.items().get(0).quoteReady()).isTrue();
        assertThat(view.items().get(0).fresh()).isTrue();
        assertThat(view.items().get(1).quoteReady()).isFalse();
    }

    private static StockWatchlistEntity watchlist(String market, String symbol, String name, int rankNo) {
        StockWatchlistEntity entity = Mockito.mock(StockWatchlistEntity.class);
        when(entity.getMarket()).thenReturn(market);
        when(entity.getSymbol()).thenReturn(symbol);
        when(entity.getName()).thenReturn(name);
        when(entity.getRankNo()).thenReturn(rankNo);
        return entity;
    }
}
