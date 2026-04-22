package discordgateway.stocknode.quote.service;

import discordgateway.stocknode.bootstrap.StockQuoteProperties;
import discordgateway.stocknode.cache.QuoteRepository;
import discordgateway.stocknode.lock.QuoteLockHandle;
import discordgateway.stocknode.lock.QuoteLockService;
import discordgateway.stocknode.quote.model.StockQuote;
import discordgateway.stocknode.quote.provider.MockQuoteProvider;
import discordgateway.stocknode.quote.provider.QuoteProvider;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

class QuoteServiceTest {

    @Test
    void returnsFreshCachedQuoteWithoutCallingProvider() {
        Instant now = Instant.parse("2026-04-22T07:00:00Z");
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        InMemoryQuoteRepository quoteRepository = new InMemoryQuoteRepository();
        quoteRepository.save(new StockQuote("US", "AAPL", new BigDecimal("120.00"), now.minusSeconds(10)), Duration.ofMinutes(10));
        MockQuoteProvider mockQuoteProvider = new MockQuoteProvider(clock);

        QuoteService quoteService = new QuoteService(
                quoteRepository,
                new InMemoryQuoteLockService(),
                mockQuoteProvider,
                (provider, instant) -> true,
                quoteProperties(),
                clock
        );

        StockQuoteResult stockQuoteResult = quoteService.getQuote("US", "aapl", QuoteUsage.QUERY);

        assertThat(stockQuoteResult.source()).isEqualTo(QuoteSource.CACHE_FRESH);
        assertThat(stockQuoteResult.fresh()).isTrue();
        assertThat(mockQuoteProvider.invocationCount()).isZero();
    }

    @Test
    void refreshesStaleTradeQuoteFromProvider() {
        Instant now = Instant.parse("2026-04-22T07:00:00Z");
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        InMemoryQuoteRepository quoteRepository = new InMemoryQuoteRepository();
        quoteRepository.save(new StockQuote("US", "AAPL", new BigDecimal("120.00"), now.minusSeconds(30)), Duration.ofMinutes(10));
        MockQuoteProvider mockQuoteProvider = new MockQuoteProvider(clock);

        QuoteService quoteService = new QuoteService(
                quoteRepository,
                new InMemoryQuoteLockService(),
                mockQuoteProvider,
                (provider, instant) -> true,
                quoteProperties(),
                clock
        );

        StockQuoteResult stockQuoteResult = quoteService.getQuote("US", "aapl", QuoteUsage.TRADE);

        assertThat(stockQuoteResult.source()).isEqualTo(QuoteSource.PROVIDER_REFRESH);
        assertThat(stockQuoteResult.fresh()).isTrue();
        assertThat(mockQuoteProvider.invocationCount()).isEqualTo(1);
    }

    @Test
    void fallsBackToStaleCacheWhenProviderIsRateLimited() {
        Instant now = Instant.parse("2026-04-22T07:00:00Z");
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        InMemoryQuoteRepository quoteRepository = new InMemoryQuoteRepository();
        StockQuote staleQuote = new StockQuote("US", "AAPL", new BigDecimal("120.00"), now.minusSeconds(30));
        quoteRepository.save(staleQuote, Duration.ofMinutes(10));
        MockQuoteProvider mockQuoteProvider = new MockQuoteProvider(clock);

        QuoteService quoteService = new QuoteService(
                quoteRepository,
                new InMemoryQuoteLockService(),
                mockQuoteProvider,
                (provider, instant) -> false,
                quoteProperties(),
                clock
        );

        StockQuoteResult stockQuoteResult = quoteService.getQuote("US", "aapl", QuoteUsage.TRADE);

        assertThat(stockQuoteResult.source()).isEqualTo(QuoteSource.CACHE_STALE);
        assertThat(stockQuoteResult.fresh()).isFalse();
        assertThat(stockQuoteResult.quote()).usingRecursiveComparison().isEqualTo(staleQuote);
        assertThat(mockQuoteProvider.invocationCount()).isZero();
    }

    @Test
    void deduplicatesConcurrentCacheMissesToOneProviderCall() throws Exception {
        Instant now = Instant.parse("2026-04-22T07:00:00Z");
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        InMemoryQuoteRepository quoteRepository = new InMemoryQuoteRepository();
        SlowQuoteProvider slowQuoteProvider = new SlowQuoteProvider(clock);
        QuoteService quoteService = new QuoteService(
                quoteRepository,
                new InMemoryQuoteLockService(),
                slowQuoteProvider,
                (provider, instant) -> true,
                quoteProperties(),
                clock
        );

        ExecutorService executorService = Executors.newFixedThreadPool(4);
        CountDownLatch startLatch = new CountDownLatch(1);
        try {
            List<Future<StockQuoteResult>> futures = new ArrayList<>();
            for (int index = 0; index < 4; index++) {
                futures.add(executorService.submit(() -> {
                    startLatch.await();
                    return quoteService.getQuote("US", "AAPL", QuoteUsage.QUERY);
                }));
            }

            startLatch.countDown();

            List<StockQuoteResult> results = collect(futures);
            assertThat(slowQuoteProvider.invocationCount()).isEqualTo(1);
            assertThat(results).hasSize(4);
            assertThat(results).allSatisfy(result -> {
                assertThat(result.quote().symbol()).isEqualTo("AAPL");
                assertThat(result.fresh()).isTrue();
            });
        } finally {
            executorService.shutdownNow();
        }
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

    private static List<StockQuoteResult> collect(List<Future<StockQuoteResult>> futures)
            throws InterruptedException, ExecutionException {
        List<StockQuoteResult> results = new ArrayList<>();
        for (Future<StockQuoteResult> future : futures) {
            results.add(future.get());
        }
        return results;
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

    private static final class InMemoryQuoteLockService implements QuoteLockService {

        private final ConcurrentMap<String, String> locks = new ConcurrentHashMap<>();

        @Override
        public Optional<QuoteLockHandle> tryAcquire(String market, String symbol) {
            String key = StockQuote.normalizeMarket(market) + ":" + StockQuote.normalizeSymbol(symbol);
            String owner = "owner-" + Thread.currentThread().getId();
            return locks.putIfAbsent(key, owner) == null
                    ? Optional.of(new QuoteLockHandle(key, owner))
                    : Optional.empty();
        }

        @Override
        public void release(QuoteLockHandle quoteLockHandle) {
            locks.remove(quoteLockHandle.key(), quoteLockHandle.ownerToken());
        }
    }

    private static final class SlowQuoteProvider implements QuoteProvider {

        private final MockQuoteProvider delegate;

        private SlowQuoteProvider(Clock clock) {
            this.delegate = new MockQuoteProvider(clock);
        }

        @Override
        public String providerName() {
            return delegate.providerName();
        }

        @Override
        public StockQuote fetchQuote(String market, String symbol) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while simulating quote latency", exception);
            }
            return delegate.fetchQuote(market, symbol);
        }

        private int invocationCount() {
            return delegate.invocationCount();
        }
    }
}
