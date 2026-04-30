package discordgateway.stocknode.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import discordgateway.stocknode.bootstrap.StockQuoteProperties;
import discordgateway.stocknode.cache.QuoteRepository;
import discordgateway.stocknode.cache.RedisQuoteRepository;
import discordgateway.stocknode.cache.StockRedisKeyFactory;
import discordgateway.stocknode.application.QuoteNotReadyException;
import discordgateway.stocknode.lock.QuoteLockHandle;
import discordgateway.stocknode.lock.QuoteLockService;
import discordgateway.stocknode.lock.RedisLockService;
import discordgateway.stocknode.quote.model.StockQuote;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
        quoteService = new QuoteService(
                quoteRepository,
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
    void returnsStaleCachedQuoteWithoutRefreshingProvider() {
        quoteRepository.save(
                new StockQuote("US", "AAPL", new BigDecimal("150.00"), clock.instant().minusSeconds(50)),
                Duration.ofMinutes(10)
        );

        StockQuoteResult result = quoteService.getQuote("US", "AAPL", QuoteUsage.QUERY);

        assertThat(result.quote().symbol()).isEqualTo("AAPL");
        assertThat(result.fresh()).isFalse();
        assertThat(result.source()).isEqualTo(QuoteSource.CACHE_STALE);
    }

    @Test
    void rejectsMissingCachedQuote() {
        assertThatThrownBy(() -> quoteService.getQuote("US", "NVDA", QuoteUsage.QUERY))
                .isInstanceOf(QuoteNotReadyException.class);
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

}
