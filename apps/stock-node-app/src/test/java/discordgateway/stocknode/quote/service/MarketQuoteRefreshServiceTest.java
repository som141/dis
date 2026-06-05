package discordgateway.stocknode.quote.service;

import discordgateway.stocknode.bootstrap.StockQuoteProperties;
import discordgateway.stocknode.cache.QuoteRepository;
import discordgateway.stocknode.lock.QuoteLockHandle;
import discordgateway.stocknode.lock.QuoteLockService;
import discordgateway.stocknode.observability.StockMetricsRecorder;
import discordgateway.stocknode.quote.model.StockQuote;
import discordgateway.stocknode.quote.provider.QuoteProvider;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketQuoteRefreshServiceTest {

    @Mock
    private QuoteRepository quoteRepository;

    @Mock
    private QuoteLockService quoteLockService;

    @Mock
    private QuoteProvider quoteProvider;

    @Mock
    private ProviderRateLimiter providerRateLimiter;

    private SimpleMeterRegistry meterRegistry;
    private MarketQuoteRefreshService marketQuoteRefreshService;

    @BeforeEach
    void setUp() {
        StockQuoteProperties stockQuoteProperties = new StockQuoteProperties();
        stockQuoteProperties.setCacheTtl(Duration.ofSeconds(60));
        meterRegistry = new SimpleMeterRegistry();
        marketQuoteRefreshService = new MarketQuoteRefreshService(
                quoteRepository,
                quoteLockService,
                quoteProvider,
                providerRateLimiter,
                stockQuoteProperties,
                new StockMetricsRecorder(meterRegistry),
                Clock.fixed(Instant.parse("2026-05-01T00:00:00Z"), ZoneOffset.UTC)
        );
        when(quoteProvider.providerName()).thenReturn("finnhub");
    }

    @Test
    void recordsSuccessMetricWhenRefreshSucceeds() {
        StockQuote stockQuote = new StockQuote("US", "NVDA", new BigDecimal("199.57"), Instant.parse("2026-05-01T00:00:00Z"));
        when(quoteLockService.tryAcquire("US", "NVDA")).thenReturn(Optional.of(new QuoteLockHandle("key", "owner")));
        when(providerRateLimiter.tryConsume("finnhub", Instant.parse("2026-05-01T00:00:00Z"))).thenReturn(true);
        when(quoteProvider.fetchQuote("US", "NVDA")).thenReturn(stockQuote);

        StockQuote result = marketQuoteRefreshService.refreshQuote("US", "NVDA");

        assertThat(result).isEqualTo(stockQuote);
        assertThat(meterRegistry.counter(
                "stock.quote.refresh.success",
                "provider", "finnhub",
                "market", "us",
                "symbol", "nvda"
        ).count()).isEqualTo(1.0);
        verify(quoteRepository).save(stockQuote, Duration.ofSeconds(60));
    }

    @Test
    void recordsRateLimitMetricWhenBudgetIsExceeded() {
        when(quoteLockService.tryAcquire("US", "NVDA")).thenReturn(Optional.of(new QuoteLockHandle("key", "owner")));
        when(providerRateLimiter.tryConsume("finnhub", Instant.parse("2026-05-01T00:00:00Z"))).thenReturn(false);

        assertThatThrownBy(() -> marketQuoteRefreshService.refreshQuote("US", "NVDA"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Provider rate limit exceeded");

        assertThat(meterRegistry.counter(
                "stock.provider.rate.limit.exceeded",
                "provider", "finnhub"
        ).count()).isEqualTo(1.0);
        assertThat(meterRegistry.counter(
                "stock.quote.refresh.failures",
                "provider", "finnhub",
                "market", "us",
                "symbol", "nvda",
                "reason", "rate_limit"
        ).count()).isEqualTo(1.0);
    }
}
