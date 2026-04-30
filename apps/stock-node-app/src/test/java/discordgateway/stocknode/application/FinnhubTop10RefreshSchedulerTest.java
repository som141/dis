package discordgateway.stocknode.application;

import discordgateway.stocknode.bootstrap.StockMarketDataProperties;
import discordgateway.stocknode.bootstrap.StockQuoteProperties;
import discordgateway.stocknode.persistence.entity.StockWatchlistEntity;
import discordgateway.stocknode.quote.service.MarketQuoteRefreshService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FinnhubTop10RefreshSchedulerTest {

    @Test
    void doesNotRunWhenProviderIsMock() {
        StockWatchlistService stockWatchlistService = mock(StockWatchlistService.class);
        MarketQuoteRefreshService marketQuoteRefreshService = mock(MarketQuoteRefreshService.class);
        StockMarketDataProperties marketDataProperties = new StockMarketDataProperties();
        marketDataProperties.setMarket("US");
        marketDataProperties.setTopRankLimit(10);
        StockQuoteProperties quoteProperties = new StockQuoteProperties();
        quoteProperties.setProvider("mock");

        FinnhubTop10RefreshScheduler scheduler = new FinnhubTop10RefreshScheduler(
                stockWatchlistService,
                marketQuoteRefreshService,
                marketDataProperties,
                quoteProperties,
                Clock.fixed(Instant.parse("2026-05-01T00:00:00Z"), ZoneOffset.UTC)
        );

        scheduler.refreshTop10Quotes();

        verify(stockWatchlistService, never()).getEnabledByMarket("US", 10);
        verify(marketQuoteRefreshService, never()).refreshQuote("US", "NVDA");
    }

    @Test
    void continuesWhenOneWatchlistSymbolFails() {
        StockWatchlistService stockWatchlistService = mock(StockWatchlistService.class);
        MarketQuoteRefreshService marketQuoteRefreshService = mock(MarketQuoteRefreshService.class);
        StockMarketDataProperties marketDataProperties = new StockMarketDataProperties();
        marketDataProperties.setMarket("US");
        marketDataProperties.setTopRankLimit(10);
        StockQuoteProperties quoteProperties = new StockQuoteProperties();
        quoteProperties.setProvider("finnhub");

        StockWatchlistEntity nvda = mock(StockWatchlistEntity.class);
        when(nvda.getMarket()).thenReturn("US");
        when(nvda.getSymbol()).thenReturn("NVDA");

        StockWatchlistEntity aapl = mock(StockWatchlistEntity.class);
        when(aapl.getMarket()).thenReturn("US");
        when(aapl.getSymbol()).thenReturn("AAPL");

        when(stockWatchlistService.getEnabledByMarket("US", 10)).thenReturn(List.of(nvda, aapl));
        when(marketQuoteRefreshService.refreshQuote("US", "NVDA")).thenThrow(new IllegalStateException("boom"));

        FinnhubTop10RefreshScheduler scheduler = new FinnhubTop10RefreshScheduler(
                stockWatchlistService,
                marketQuoteRefreshService,
                marketDataProperties,
                quoteProperties,
                Clock.fixed(Instant.parse("2026-05-01T00:00:00Z"), ZoneOffset.UTC)
        );

        scheduler.refreshTop10Quotes();

        verify(marketQuoteRefreshService).refreshQuote("US", "NVDA");
        verify(marketQuoteRefreshService).refreshQuote("US", "AAPL");
    }
}
