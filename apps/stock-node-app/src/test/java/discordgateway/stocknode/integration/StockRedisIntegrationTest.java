package discordgateway.stocknode.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import discordgateway.stocknode.bootstrap.StockQuoteProperties;
import discordgateway.stocknode.cache.QuoteRepository;
import discordgateway.stocknode.cache.RedisQuoteRepository;
import discordgateway.stocknode.cache.StockRedisKeyFactory;
import discordgateway.stocknode.lock.QuoteLockHandle;
import discordgateway.stocknode.lock.QuoteLockService;
import discordgateway.stocknode.lock.RedisLockService;
import discordgateway.stocknode.quote.model.StockQuote;
import discordgateway.stocknode.quote.provider.MockQuoteProvider;
import discordgateway.stocknode.quote.service.ProviderRateLimitService;
import discordgateway.stocknode.quote.service.ProviderRateLimiter;
import discordgateway.stocknode.quote.service.QuoteService;
import discordgateway.stocknode.quote.service.QuoteSource;
import discordgateway.stocknode.quote.service.QuoteUsage;
import discordgateway.stocknode.quote.service.StockQuoteResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

class StockRedisIntegrationTest extends StockNodeIntegrationTestSupport {

    private final Clock clock = Clock.fixed(Instant.parse("2026-04-22T07:00:00Z"), ZoneOffset.UTC);
    private final StockRedisKeyFactory stockRedisKeyFactory = new StockRedisKeyFactory();
    private final StockQuoteProperties stockQuoteProperties = quoteProperties();

    private LettuceConnectionFactory lettuceConnectionFactory;
    private StringRedisTemplate stringRedisTemplate;
    private QuoteRepository quoteRepository;
    private QuoteLockService quoteLockService;
    private ProviderRateLimiter providerRateLimiter;
    private MockQuoteProvider mockQuoteProvider;
    private QuoteService quoteService;

    @BeforeEach
    void setUp() {
        RedisStandaloneConfiguration redisConfiguration = new RedisStandaloneConfiguration(
                REDIS_CONTAINER.getHost(),
                REDIS_CONTAINER.getMappedPort(6379)
        );
        lettuceConnectionFactory = new LettuceConnectionFactory(redisConfiguration);
        lettuceConnectionFactory.afterPropertiesSet();

        stringRedisTemplate = new StringRedisTemplate(lettuceConnectionFactory);
        stringRedisTemplate.afterPropertiesSet();

        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        quoteRepository = new RedisQuoteRepository(stringRedisTemplate, objectMapper, stockRedisKeyFactory);
        quoteLockService = new RedisLockService(
                stringRedisTemplate,
                stockRedisKeyFactory,
                stockQuoteProperties.getLockTtl()
        );
        providerRateLimiter = new ProviderRateLimitService(
                stringRedisTemplate,
                stockRedisKeyFactory,
                stockQuoteProperties
        );
        mockQuoteProvider = new MockQuoteProvider(clock);
        quoteService = new QuoteService(
                quoteRepository,
                quoteLockService,
                mockQuoteProvider,
                providerRateLimiter,
                stockQuoteProperties,
                clock
        );

        stringRedisTemplate.getConnectionFactory()
                .getConnection()
                .serverCommands()
                .flushDb();
    }

    @AfterEach
    void tearDown() {
        if (lettuceConnectionFactory != null) {
            lettuceConnectionFactory.destroy();
        }
    }

    @Test
    void storesAndLoadsQuoteFromRealRedis() {
        StockQuote stockQuote = new StockQuote(
                "US",
                "AAPL",
                new BigDecimal("123.45"),
                Instant.parse("2026-04-22T07:00:00Z")
        );

        quoteRepository.save(stockQuote, Duration.ofMinutes(10));

        assertThat(quoteRepository.find("us", "aapl"))
                .isPresent()
                .get()
                .usingRecursiveComparison()
                .isEqualTo(stockQuote);
    }

    @Test
    void acquiresOneLockPerQuoteKeyOnRealRedis() {
        Optional<QuoteLockHandle> firstLock = quoteLockService.tryAcquire("US", "AAPL");
        Optional<QuoteLockHandle> secondLock = quoteLockService.tryAcquire("US", "AAPL");

        assertThat(firstLock).isPresent();
        assertThat(secondLock).isEmpty();

        firstLock.ifPresent(quoteLockService::release);

        assertThat(quoteLockService.tryAcquire("US", "AAPL")).isPresent();
    }

    @Test
    void tracksProviderBudgetOnRealRedis() {
        Instant now = Instant.parse("2026-04-22T07:05:31Z");

        assertThat(providerRateLimiter.tryConsume("mock", now)).isTrue();
        assertThat(providerRateLimiter.tryConsume("mock", now)).isTrue();
        assertThat(providerRateLimiter.tryConsume("mock", now)).isTrue();
    }

    @Test
    void deduplicatesConcurrentQuoteRefreshOnRealRedis() throws Exception {
        ExecutorService executorService = Executors.newFixedThreadPool(4);
        CountDownLatch startLatch = new CountDownLatch(1);
        try {
            int initialInvocationCount = mockQuoteProvider.invocationCount();
            List<Future<StockQuoteResult>> futures = new ArrayList<>();
            for (int index = 0; index < 4; index++) {
                futures.add(executorService.submit(() -> {
                    startLatch.await();
                    return quoteService.getQuote("US", "AAPL", QuoteUsage.QUERY);
                }));
            }

            startLatch.countDown();

            List<StockQuoteResult> results = collect(futures);

            assertThat(mockQuoteProvider.invocationCount() - initialInvocationCount).isEqualTo(1);
            assertThat(results).hasSize(4);
            assertThat(results).allSatisfy(result -> {
                assertThat(result.quote().symbol()).isEqualTo("AAPL");
                assertThat(result.fresh()).isTrue();
                assertThat(result.source()).isIn(QuoteSource.PROVIDER_MISS, QuoteSource.CACHE_FRESH);
            });
        } finally {
            executorService.shutdownNow();
        }
    }

    private static StockQuoteProperties quoteProperties() {
        StockQuoteProperties properties = new StockQuoteProperties();
        properties.setCacheTtl(Duration.ofMinutes(10));
        properties.setQueryFreshness(Duration.ofSeconds(30));
        properties.setTradeFreshness(Duration.ofSeconds(5));
        properties.setRankFreshness(Duration.ofMinutes(5));
        properties.setLockTtl(Duration.ofSeconds(2));
        properties.setLockWaitTimeout(Duration.ofSeconds(1));
        properties.setLockPollInterval(Duration.ofMillis(10));
        properties.setProviderPerMinuteLimit(60);
        properties.setProviderPerDayLimit(5_000);
        return properties;
    }

    private static List<StockQuoteResult> collect(List<Future<StockQuoteResult>> futures)
            throws InterruptedException, ExecutionException {
        List<StockQuoteResult> results = new ArrayList<>();
        for (Future<StockQuoteResult> future : futures) {
            results.add(future.get());
        }
        return results;
    }
}
