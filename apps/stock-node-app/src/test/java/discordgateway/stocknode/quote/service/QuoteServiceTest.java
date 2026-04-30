package discordgateway.stocknode.quote.service;

import discordgateway.stocknode.bootstrap.StockQuoteProperties;
import discordgateway.stocknode.application.QuoteNotReadyException;
import discordgateway.stocknode.cache.QuoteRepository;
import discordgateway.stocknode.quote.model.StockQuote;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuoteServiceTest {

    @Test
    void returnsFreshCachedQuoteWithoutCallingProvider() {
        Instant now = Instant.parse("2026-04-22T07:00:00Z");
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        InMemoryQuoteRepository quoteRepository = new InMemoryQuoteRepository();
        quoteRepository.save(new StockQuote("US", "AAPL", new BigDecimal("120.00"), now.minusSeconds(10)), Duration.ofMinutes(10));

        QuoteService quoteService = new QuoteService(
                quoteRepository,
                quoteProperties(),
                clock
        );

        StockQuoteResult stockQuoteResult = quoteService.getQuote("US", "aapl", QuoteUsage.QUERY);

        assertThat(stockQuoteResult.source()).isEqualTo(QuoteSource.CACHE_FRESH);
        assertThat(stockQuoteResult.fresh()).isTrue();
    }

    @Test
    void returnsStaleTradeQuoteFromCache() {
        Instant now = Instant.parse("2026-04-22T07:00:00Z");
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        InMemoryQuoteRepository quoteRepository = new InMemoryQuoteRepository();
        quoteRepository.save(new StockQuote("US", "AAPL", new BigDecimal("120.00"), now.minusSeconds(30)), Duration.ofMinutes(10));

        QuoteService quoteService = new QuoteService(
                quoteRepository,
                quoteProperties(),
                clock
        );

        StockQuoteResult stockQuoteResult = quoteService.getQuote("US", "aapl", QuoteUsage.TRADE);

        assertThat(stockQuoteResult.source()).isEqualTo(QuoteSource.CACHE_STALE);
        assertThat(stockQuoteResult.fresh()).isFalse();
    }

    @Test
    void returnsStaleCacheForQueryWhenFreshnessWindowPassed() {
        Instant now = Instant.parse("2026-04-22T07:00:00Z");
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        InMemoryQuoteRepository quoteRepository = new InMemoryQuoteRepository();
        StockQuote staleQuote = new StockQuote("US", "AAPL", new BigDecimal("120.00"), now.minusSeconds(50));
        quoteRepository.save(staleQuote, Duration.ofMinutes(10));

        QuoteService quoteService = new QuoteService(
                quoteRepository,
                quoteProperties(),
                clock
        );

        StockQuoteResult stockQuoteResult = quoteService.getQuote("US", "aapl", QuoteUsage.QUERY);

        assertThat(stockQuoteResult.source()).isEqualTo(QuoteSource.CACHE_STALE);
        assertThat(stockQuoteResult.fresh()).isFalse();
        assertThat(stockQuoteResult.quote()).usingRecursiveComparison().isEqualTo(staleQuote);
    }

    @Test
    void rejectsMissingCachedQuote() {
        Instant now = Instant.parse("2026-04-22T07:00:00Z");
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        InMemoryQuoteRepository quoteRepository = new InMemoryQuoteRepository();
        QuoteService quoteService = new QuoteService(
                quoteRepository,
                quoteProperties(),
                clock
        );

        assertThatThrownBy(() -> quoteService.getQuote("US", "AAPL", QuoteUsage.QUERY))
                .isInstanceOf(QuoteNotReadyException.class);
    }

    private static StockQuoteProperties quoteProperties() {
        StockQuoteProperties stockQuoteProperties = new StockQuoteProperties();
        stockQuoteProperties.setCacheTtl(Duration.ofMinutes(10));
        stockQuoteProperties.setQueryFreshness(Duration.ofSeconds(30));
        stockQuoteProperties.setTradeFreshness(Duration.ofSeconds(5));
        stockQuoteProperties.setRankFreshness(Duration.ofMinutes(5));
        stockQuoteProperties.setLockTtl(Duration.ofSeconds(2));
        stockQuoteProperties.setLockWaitTimeout(Duration.ofSeconds(1));
        stockQuoteProperties.setLockPollInterval(Duration.ofMillis(10));
        return stockQuoteProperties;
    }

    private static final class InMemoryQuoteRepository implements QuoteRepository {

        private final ConcurrentMap<String, StockQuote> quotes = new ConcurrentHashMap<>();

        @Override
        public Optional<StockQuote> find(String market, String symbol) {
            return Optional.ofNullable(quotes.get(key(market, symbol)));
        }

        @Override
        public void save(StockQuote quote, Duration ttl) {
            quotes.put(key(quote.market(), quote.symbol()), quote);
        }

        private String key(String market, String symbol) {
            return StockQuote.normalizeMarket(market) + ":" + StockQuote.normalizeSymbol(symbol);
        }
    }
}
