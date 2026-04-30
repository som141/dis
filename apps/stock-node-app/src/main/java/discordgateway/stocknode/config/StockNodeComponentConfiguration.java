package discordgateway.stocknode.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import discordgateway.stock.messaging.StockMessagingProperties;
import discordgateway.stocknode.application.BalanceQueryService;
import discordgateway.stocknode.application.DailyAllowanceService;
import discordgateway.stocknode.application.PortfolioQueryService;
import discordgateway.stocknode.application.PortfolioService;
import discordgateway.stocknode.application.RankingService;
import discordgateway.stocknode.application.SnapshotScheduler;
import discordgateway.stocknode.application.SnapshotService;
import discordgateway.stocknode.application.StockAccountApplicationService;
import discordgateway.stocknode.application.StockCommandApplicationService;
import discordgateway.stocknode.application.StockResponseFormatter;
import discordgateway.stocknode.application.TradeExecutionService;
import discordgateway.stocknode.application.TradeHistoryQueryService;
import discordgateway.stocknode.bootstrap.StockNodeStorageProperties;
import discordgateway.stocknode.bootstrap.StockProviderProperties;
import discordgateway.stocknode.bootstrap.StockQuoteProperties;
import discordgateway.stocknode.cache.QuoteRepository;
import discordgateway.stocknode.cache.RankingCacheRepository;
import discordgateway.stocknode.cache.RedisQuoteRepository;
import discordgateway.stocknode.cache.RedisRankingCacheRepository;
import discordgateway.stocknode.cache.StockRedisKeyFactory;
import discordgateway.stocknode.lock.QuoteLockService;
import discordgateway.stocknode.lock.RedisLockService;
import discordgateway.stocknode.persistence.repository.AccountSnapshotRepository;
import discordgateway.stocknode.persistence.repository.AllowanceLedgerRepository;
import discordgateway.stocknode.persistence.repository.StockAccountRepository;
import discordgateway.stocknode.persistence.repository.StockPositionRepository;
import discordgateway.stocknode.persistence.repository.TradeLedgerRepository;
import discordgateway.stocknode.quote.provider.AlphaVantageQuoteProvider;
import discordgateway.stocknode.quote.provider.FallbackQuoteProvider;
import discordgateway.stocknode.quote.provider.MockQuoteProvider;
import discordgateway.stocknode.quote.provider.QuoteProvider;
import discordgateway.stocknode.quote.service.ProviderRateLimitService;
import discordgateway.stocknode.quote.service.ProviderRateLimiter;
import discordgateway.stocknode.quote.service.QuoteService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({
        StockMessagingProperties.class,
        StockNodeStorageProperties.class,
        StockProviderProperties.class,
        StockQuoteProperties.class
})
public class StockNodeComponentConfiguration {

    @Bean
    public StockAccountApplicationService stockAccountApplicationService(
            StockAccountRepository stockAccountRepository
    ) {
        return new StockAccountApplicationService(stockAccountRepository);
    }

    @Bean
    public Clock stockClock() {
        return Clock.systemUTC();
    }

    @Bean
    public StockRedisKeyFactory stockRedisKeyFactory() {
        return new StockRedisKeyFactory();
    }

    @Bean
    public QuoteRepository quoteRepository(
            StringRedisTemplate stringRedisTemplate,
            ObjectMapper objectMapper,
            StockRedisKeyFactory stockRedisKeyFactory
    ) {
        return new RedisQuoteRepository(stringRedisTemplate, objectMapper, stockRedisKeyFactory);
    }

    @Bean
    public RankingCacheRepository rankingCacheRepository(
            StringRedisTemplate stringRedisTemplate,
            ObjectMapper objectMapper,
            StockRedisKeyFactory stockRedisKeyFactory
    ) {
        return new RedisRankingCacheRepository(stringRedisTemplate, objectMapper, stockRedisKeyFactory);
    }

    @Bean
    public QuoteLockService quoteLockService(
            StringRedisTemplate stringRedisTemplate,
            StockRedisKeyFactory stockRedisKeyFactory,
            StockQuoteProperties stockQuoteProperties
    ) {
        return new RedisLockService(
                stringRedisTemplate,
                stockRedisKeyFactory,
                stockQuoteProperties.getLockTtl()
        );
    }

    @Bean
    public ProviderRateLimiter providerRateLimiter(
            StringRedisTemplate stringRedisTemplate,
            StockRedisKeyFactory stockRedisKeyFactory,
            StockQuoteProperties stockQuoteProperties
    ) {
        return new ProviderRateLimitService(
                stringRedisTemplate,
                stockRedisKeyFactory,
                stockQuoteProperties
        );
    }

    @Bean
    public MockQuoteProvider mockQuoteProvider(Clock stockClock) {
        return new MockQuoteProvider(stockClock);
    }

    @Bean
    public AlphaVantageQuoteProvider alphaVantageQuoteProvider(
            ObjectMapper objectMapper,
            StockProviderProperties stockProviderProperties,
            Clock stockClock
    ) {
        return new AlphaVantageQuoteProvider(objectMapper, stockProviderProperties, stockClock);
    }

    @Bean
    public QuoteProvider quoteProvider(
            MockQuoteProvider mockQuoteProvider,
            AlphaVantageQuoteProvider alphaVantageQuoteProvider,
            StockProviderProperties stockProviderProperties
    ) {
        String providerType = stockProviderProperties.getType() == null
                ? "mock"
                : stockProviderProperties.getType().trim().toLowerCase();

        return switch (providerType) {
            case "mock" -> mockQuoteProvider;
            case "alphavantage" -> new FallbackQuoteProvider(
                    alphaVantageQuoteProvider,
                    mockQuoteProvider,
                    stockProviderProperties.isFallbackToMock()
            );
            default -> throw new IllegalArgumentException("Unsupported stock quote provider type: " + providerType);
        };
    }

    @Bean
    public QuoteService quoteService(
            QuoteRepository quoteRepository,
            QuoteLockService quoteLockService,
            QuoteProvider quoteProvider,
            ProviderRateLimiter providerRateLimiter,
            StockQuoteProperties stockQuoteProperties,
            Clock stockClock
    ) {
        return new QuoteService(
                quoteRepository,
                quoteLockService,
                quoteProvider,
                providerRateLimiter,
                stockQuoteProperties,
                stockClock
        );
    }

    @Bean
    public DailyAllowanceService dailyAllowanceService(
            StockAccountApplicationService stockAccountApplicationService,
            StockAccountRepository stockAccountRepository,
            AllowanceLedgerRepository allowanceLedgerRepository,
            RankingCacheRepository rankingCacheRepository,
            Clock stockClock
    ) {
        return new DailyAllowanceService(
                stockAccountApplicationService,
                stockAccountRepository,
                allowanceLedgerRepository,
                rankingCacheRepository,
                stockClock
        );
    }

    @Bean
    public TradeExecutionService tradeExecutionService(
            DailyAllowanceService dailyAllowanceService,
            StockAccountRepository stockAccountRepository,
            StockPositionRepository stockPositionRepository,
            TradeLedgerRepository tradeLedgerRepository,
            QuoteService quoteService,
            RankingCacheRepository rankingCacheRepository,
            StockQuoteProperties stockQuoteProperties,
            Clock stockClock
    ) {
        return new TradeExecutionService(
                dailyAllowanceService,
                stockAccountRepository,
                stockPositionRepository,
                tradeLedgerRepository,
                quoteService,
                rankingCacheRepository,
                stockQuoteProperties,
                stockClock
        );
    }

    @Bean
    public PortfolioService portfolioService(
            StockPositionRepository stockPositionRepository,
            QuoteService quoteService,
            StockQuoteProperties stockQuoteProperties
    ) {
        return new PortfolioService(
                stockPositionRepository,
                quoteService,
                stockQuoteProperties
        );
    }

    @Bean
    public BalanceQueryService balanceQueryService(DailyAllowanceService dailyAllowanceService) {
        return new BalanceQueryService(dailyAllowanceService);
    }

    @Bean
    public PortfolioQueryService portfolioQueryService(
            DailyAllowanceService dailyAllowanceService,
            PortfolioService portfolioService
    ) {
        return new PortfolioQueryService(dailyAllowanceService, portfolioService);
    }

    @Bean
    public TradeHistoryQueryService tradeHistoryQueryService(
            DailyAllowanceService dailyAllowanceService,
            TradeLedgerRepository tradeLedgerRepository
    ) {
        return new TradeHistoryQueryService(dailyAllowanceService, tradeLedgerRepository);
    }

    @Bean
    public SnapshotService snapshotService(
            StockAccountRepository stockAccountRepository,
            AccountSnapshotRepository accountSnapshotRepository,
            DailyAllowanceService dailyAllowanceService,
            PortfolioService portfolioService,
            Clock stockClock
    ) {
        return new SnapshotService(
                stockAccountRepository,
                accountSnapshotRepository,
                dailyAllowanceService,
                portfolioService,
                stockClock
        );
    }

    @Bean
    public SnapshotScheduler snapshotScheduler(SnapshotService snapshotService) {
        return new SnapshotScheduler(snapshotService);
    }

    @Bean
    public RankingService rankingService(
            StockAccountRepository stockAccountRepository,
            AllowanceLedgerRepository allowanceLedgerRepository,
            DailyAllowanceService dailyAllowanceService,
            PortfolioService portfolioService,
            SnapshotService snapshotService,
            RankingCacheRepository rankingCacheRepository,
            StockQuoteProperties stockQuoteProperties,
            Clock stockClock
    ) {
        return new RankingService(
                stockAccountRepository,
                allowanceLedgerRepository,
                dailyAllowanceService,
                portfolioService,
                snapshotService,
                rankingCacheRepository,
                stockQuoteProperties,
                stockClock
        );
    }

    @Bean
    public StockResponseFormatter stockResponseFormatter() {
        return new StockResponseFormatter();
    }

    @Bean
    public StockCommandApplicationService stockCommandApplicationService(
            QuoteService quoteService,
            TradeExecutionService tradeExecutionService,
            BalanceQueryService balanceQueryService,
            PortfolioQueryService portfolioQueryService,
            TradeHistoryQueryService tradeHistoryQueryService,
            RankingService rankingService,
            StockResponseFormatter stockResponseFormatter,
            StockQuoteProperties stockQuoteProperties,
            Clock stockClock,
            @Value("${app.node-name:stock-node-1}") String producerNode
    ) {
        return new StockCommandApplicationService(
                quoteService,
                tradeExecutionService,
                balanceQueryService,
                portfolioQueryService,
                tradeHistoryQueryService,
                rankingService,
                stockResponseFormatter,
                stockQuoteProperties,
                stockClock,
                producerNode
        );
    }
}
