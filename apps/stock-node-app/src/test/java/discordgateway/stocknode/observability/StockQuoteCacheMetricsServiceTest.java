package discordgateway.stocknode.observability;

import discordgateway.stocknode.cache.QuoteRepository;
import discordgateway.stocknode.persistence.entity.StockWatchlistEntity;
import discordgateway.stocknode.quote.model.StockQuote;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StockQuoteCacheMetricsServiceTest {

    @Test
    void recordsWatchlistQuoteCacheReadinessAndStaleness() {
        Instant now = Instant.parse("2026-05-01T00:01:00Z");
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        StockMetricsRecorder recorder = new StockMetricsRecorder(meterRegistry);
        QuoteRepository quoteRepository = mock(QuoteRepository.class);

        StockWatchlistEntity nvda = watchlistItem("US", "NVDA");
        StockWatchlistEntity aapl = watchlistItem("US", "AAPL");
        StockWatchlistEntity msft = watchlistItem("US", "MSFT");

        when(quoteRepository.find("US", "NVDA"))
                .thenReturn(Optional.of(new StockQuote("US", "NVDA", BigDecimal.valueOf(100), now.minusSeconds(20))));
        when(quoteRepository.find("US", "AAPL"))
                .thenReturn(Optional.of(new StockQuote("US", "AAPL", BigDecimal.valueOf(200), now.minusSeconds(50))));
        when(quoteRepository.find("US", "MSFT"))
                .thenReturn(Optional.empty());

        StockQuoteCacheMetricsService service = new StockQuoteCacheMetricsService(
                quoteRepository,
                recorder,
                Clock.fixed(now, ZoneOffset.UTC)
        );

        service.recordWatchlistCacheState("US", List.of(nvda, aapl, msft), Duration.ofSeconds(45));

        assertThat(meterRegistry.find("stock.quote.cache.expected").tag("market", "us").gauge().value())
                .isEqualTo(3.0);
        assertThat(meterRegistry.find("stock.quote.cache.ready").tag("market", "us").gauge().value())
                .isEqualTo(2.0);
        assertThat(meterRegistry.find("stock.quote.cache.stale").tag("market", "us").gauge().value())
                .isEqualTo(1.0);
        assertThat(meterRegistry.find("stock.quote.cache.oldest.age").tag("market", "us").gauge().value())
                .isEqualTo(50.0);
    }

    private StockWatchlistEntity watchlistItem(String market, String symbol) {
        StockWatchlistEntity item = mock(StockWatchlistEntity.class);
        when(item.getMarket()).thenReturn(market);
        when(item.getSymbol()).thenReturn(symbol);
        return item;
    }
}
