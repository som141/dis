package discordgateway.stocknode.application;

import discordgateway.stocknode.bootstrap.StockMarketDataProperties;
import discordgateway.stocknode.bootstrap.StockQuoteProperties;
import discordgateway.stocknode.persistence.entity.StockWatchlistEntity;
import discordgateway.stocknode.quote.model.StockQuote;
import discordgateway.stocknode.quote.service.MarketQuoteRefreshService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

public class FinnhubTop10RefreshScheduler {

    private static final Logger log = LoggerFactory.getLogger(FinnhubTop10RefreshScheduler.class);

    private final StockWatchlistService stockWatchlistService;
    private final MarketQuoteRefreshService marketQuoteRefreshService;
    private final StockMarketDataProperties stockMarketDataProperties;
    private final StockQuoteProperties stockQuoteProperties;
    private final Clock clock;

    public FinnhubTop10RefreshScheduler(
            StockWatchlistService stockWatchlistService,
            MarketQuoteRefreshService marketQuoteRefreshService,
            StockMarketDataProperties stockMarketDataProperties,
            StockQuoteProperties stockQuoteProperties,
            Clock clock
    ) {
        this.stockWatchlistService = stockWatchlistService;
        this.marketQuoteRefreshService = marketQuoteRefreshService;
        this.stockMarketDataProperties = stockMarketDataProperties;
        this.stockQuoteProperties = stockQuoteProperties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${stock.market-data.refresh-fixed-delay-ms:20000}")
    public void refreshTop10Quotes() {
        if (!stockMarketDataProperties.isEnabled()) {
            return;
        }
        if (!"finnhub".equalsIgnoreCase(stockQuoteProperties.getProvider())) {
            return;
        }

        Instant startedAt = clock.instant();
        List<StockWatchlistEntity> watchlist = stockWatchlistService.getEnabledByMarket(
                stockMarketDataProperties.getMarket(),
                stockMarketDataProperties.getTopRankLimit()
        );

        int successCount = 0;
        int failureCount = 0;
        for (StockWatchlistEntity item : watchlist) {
            try {
                StockQuote refreshedQuote = marketQuoteRefreshService.refreshQuote(item.getMarket(), item.getSymbol());
                successCount++;
                log.info("refreshed stock quote from Finnhub market={} symbol={}", refreshedQuote.market(), refreshedQuote.symbol());
            } catch (Exception exception) {
                failureCount++;
                log.warn(
                        "failed to refresh stock quote from Finnhub market={} symbol={}",
                        item.getMarket(),
                        item.getSymbol(),
                        exception
                );
            }
        }

        log.info(
                "completed stock watchlist refresh run market={} startedAt={} successCount={} failureCount={}",
                stockMarketDataProperties.getMarket().toUpperCase(Locale.ROOT),
                startedAt,
                successCount,
                failureCount
        );
    }
}
