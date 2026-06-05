package discordgateway.stocknode.observability;

import discordgateway.stocknode.cache.QuoteRepository;
import discordgateway.stocknode.persistence.entity.StockWatchlistEntity;
import discordgateway.stocknode.quote.model.StockQuote;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

public class StockQuoteCacheMetricsService {

    private final QuoteRepository quoteRepository;
    private final StockMetricsRecorder stockMetricsRecorder;
    private final Clock clock;

    public StockQuoteCacheMetricsService(
            QuoteRepository quoteRepository,
            StockMetricsRecorder stockMetricsRecorder,
            Clock clock
    ) {
        this.quoteRepository = quoteRepository;
        this.stockMetricsRecorder = stockMetricsRecorder;
        this.clock = clock;
    }

    public void recordWatchlistCacheState(
            String market,
            List<StockWatchlistEntity> watchlist,
            Duration freshness
    ) {
        int readyCount = 0;
        int staleCount = 0;
        long oldestAgeSeconds = 0L;

        for (StockWatchlistEntity item : watchlist) {
            StockQuote quote = quoteRepository.find(item.getMarket(), item.getSymbol()).orElse(null);
            if (quote == null) {
                continue;
            }

            readyCount++;
            long ageSeconds = Math.max(Duration.between(quote.quotedAt(), clock.instant()).toSeconds(), 0L);
            oldestAgeSeconds = Math.max(oldestAgeSeconds, ageSeconds);
            if (ageSeconds > freshness.toSeconds()) {
                staleCount++;
            }
        }

        stockMetricsRecorder.recordQuoteCacheState(
                market,
                watchlist.size(),
                readyCount,
                staleCount,
                oldestAgeSeconds
        );
    }
}
