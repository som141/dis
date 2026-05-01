package discordgateway.stocknode.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import discordgateway.stock.messaging.StockMessagingProperties;
import discordgateway.stocknode.application.AutoLiquidationService;
import discordgateway.stocknode.application.BalanceQueryService;
import discordgateway.stocknode.application.DailyAllowanceService;
import discordgateway.stocknode.application.FinnhubTop10RefreshScheduler;
import discordgateway.stocknode.application.MonthlySeasonScheduler;
import discordgateway.stocknode.application.PortfolioQueryService;
import discordgateway.stocknode.application.PortfolioService;
import discordgateway.stocknode.application.RankingService;
import discordgateway.stocknode.application.SnapshotScheduler;
import discordgateway.stocknode.application.SnapshotService;
import discordgateway.stocknode.application.StockAccountApplicationService;
import discordgateway.stocknode.application.StockCommandApplicationService;
import discordgateway.stocknode.application.StockListQueryService;
import discordgateway.stocknode.application.StockResponseFormatter;
import discordgateway.stocknode.application.StockSeasonService;
import discordgateway.stocknode.application.StockWatchlistService;
import discordgateway.stocknode.application.TradeExecutionService;
import discordgateway.stocknode.application.TradeHistoryQueryService;
import discordgateway.stocknode.bootstrap.FinnhubProperties;
import discordgateway.stocknode.bootstrap.StockMarketDataProperties;
import discordgateway.stocknode.bootstrap.StockNodeStorageProperties;
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
import discordgateway.stocknode.persistence.repository.StockWatchlistRepository;
import discordgateway.stocknode.persistence.repository.TradeLedgerRepository;
import discordgateway.stocknode.quote.finnhub.FinnhubClient;
import discordgateway.stocknode.quote.finnhub.FinnhubQuoteMapper;
import discordgateway.stocknode.quote.provider.FinnhubQuoteProvider;
import discordgateway.stocknode.quote.provider.MockQuoteProvider;
import discordgateway.stocknode.quote.provider.QuoteProvider;
import discordgateway.stocknode.quote.service.MarketQuoteRefreshService;
import discordgateway.stocknode.quote.service.ProviderRateLimitService;
import discordgateway.stocknode.quote.service.ProviderRateLimiter;
import discordgateway.stocknode.quote.service.QuoteService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({
        StockMessagingProperties.class,
        StockNodeStorageProperties.class,
        StockQuoteProperties.class,
        StockMarketDataProperties.class,
        FinnhubProperties.class
})
public class StockNodeComponentConfiguration {

    @Bean
    public StockAccountApplicationService stockAccountApplicationService(
            StockAccountRepository stockAccountRepository,
            StockSeasonService stockSeasonService
    ) {
        return new StockAccountApplicationService(stockAccountRepository, stockSeasonService);
    }

    @Bean
    public StockWatchlistService stockWatchlistService(StockWatchlistRepository stockWatchlistRepository) {
        return new StockWatchlistService(stockWatchlistRepository);
    }

    @Bean
    public Clock stockClock() {
        return Clock.systemUTC();
    }

    @Bean
    public StockSeasonService stockSeasonService(Clock stockClock) {
        return new StockSeasonService(stockClock);
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
    public WebClient finnhubWebClient(FinnhubProperties finnhubProperties) {
        return WebClient.builder()
                .baseUrl(finnhubProperties.getBaseUrl())
                .build();
    }

    @Bean
    public FinnhubClient finnhubClient(WebClient finnhubWebClient, FinnhubProperties finnhubProperties) {
        return new FinnhubClient(finnhubWebClient, finnhubProperties);
    }

    @Bean
    public FinnhubQuoteMapper finnhubQuoteMapper(Clock stockClock) {
        return new FinnhubQuoteMapper(stockClock);
    }

    @Bean
    public FinnhubQuoteProvider finnhubQuoteProvider(
            FinnhubClient finnhubClient,
            FinnhubQuoteMapper finnhubQuoteMapper,
            StockWatchlistService stockWatchlistService,
            FinnhubProperties finnhubProperties
    ) {
        return new FinnhubQuoteProvider(
                finnhubClient,
                finnhubQuoteMapper,
                stockWatchlistService,
                finnhubProperties
        );
    }

    @Bean
    public QuoteProvider quoteProvider(
            MockQuoteProvider mockQuoteProvider,
            FinnhubQuoteProvider finnhubQuoteProvider,
            StockQuoteProperties stockQuoteProperties,
            FinnhubProperties finnhubProperties
    ) {
        String providerType = stockQuoteProperties.getProvider() == null
                ? "mock"
                : stockQuoteProperties.getProvider().trim().toLowerCase();

        if ("finnhub".equals(providerType)
                && (finnhubProperties.getApiKey() == null || finnhubProperties.getApiKey().isBlank())) {
            throw new IllegalStateException("FINNHUB_API_KEY must be set when stock.quote.provider=finnhub");
        }

        return switch (providerType) {
            case "mock" -> mockQuoteProvider;
            case "finnhub" -> finnhubQuoteProvider;
            default -> throw new IllegalArgumentException("Unsupported stock quote provider type: " + providerType);
        };
    }

    @Bean
    public QuoteService quoteService(
            QuoteRepository quoteRepository,
            StockQuoteProperties stockQuoteProperties,
            Clock stockClock
    ) {
        return new QuoteService(
                quoteRepository,
                stockQuoteProperties,
                stockClock
        );
    }

    @Bean
    public MarketQuoteRefreshService marketQuoteRefreshService(
            QuoteRepository quoteRepository,
            QuoteLockService quoteLockService,
            QuoteProvider quoteProvider,
            ProviderRateLimiter providerRateLimiter,
            StockQuoteProperties stockQuoteProperties,
            Clock stockClock
    ) {
        return new MarketQuoteRefreshService(
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
            StockWatchlistService stockWatchlistService,
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
                stockWatchlistService,
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
    public AutoLiquidationService autoLiquidationService(
            StockPositionRepository stockPositionRepository,
            TradeLedgerRepository tradeLedgerRepository,
            RankingCacheRepository rankingCacheRepository,
            Clock stockClock,
            PlatformTransactionManager transactionManager
    ) {
        return new AutoLiquidationService(
                stockPositionRepository,
                tradeLedgerRepository,
                rankingCacheRepository,
                stockClock,
                new TransactionTemplate(transactionManager)
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
    public StockListQueryService stockListQueryService(
            StockWatchlistService stockWatchlistService,
            QuoteService quoteService,
            StockMarketDataProperties stockMarketDataProperties
    ) {
        return new StockListQueryService(stockWatchlistService, quoteService, stockMarketDataProperties);
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
            StockAccountApplicationService stockAccountApplicationService,
            PortfolioService portfolioService,
            Clock stockClock
    ) {
        return new SnapshotService(
                stockAccountRepository,
                accountSnapshotRepository,
                dailyAllowanceService,
                stockAccountApplicationService,
                portfolioService,
                stockClock
        );
    }

    @Bean
    public SnapshotScheduler snapshotScheduler(SnapshotService snapshotService) {
        return new SnapshotScheduler(snapshotService);
    }

    @Bean
    public FinnhubTop10RefreshScheduler finnhubTop10RefreshScheduler(
            StockWatchlistService stockWatchlistService,
            MarketQuoteRefreshService marketQuoteRefreshService,
            AutoLiquidationService autoLiquidationService,
            StockMarketDataProperties stockMarketDataProperties,
            StockQuoteProperties stockQuoteProperties,
            Clock stockClock
    ) {
        return new FinnhubTop10RefreshScheduler(
                stockWatchlistService,
                marketQuoteRefreshService,
                autoLiquidationService,
                stockMarketDataProperties,
                stockQuoteProperties,
                stockClock
        );
    }

    @Bean
    public MonthlySeasonScheduler monthlySeasonScheduler(StockSeasonService stockSeasonService) {
        return new MonthlySeasonScheduler(stockSeasonService);
    }

    @Bean
    public RankingService rankingService(
            StockAccountRepository stockAccountRepository,
            AllowanceLedgerRepository allowanceLedgerRepository,
            DailyAllowanceService dailyAllowanceService,
            StockAccountApplicationService stockAccountApplicationService,
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
                stockAccountApplicationService,
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
            StockListQueryService stockListQueryService,
            StockWatchlistService stockWatchlistService,
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
                stockListQueryService,
                stockWatchlistService,
                rankingService,
                stockResponseFormatter,
                stockQuoteProperties,
                stockClock,
                producerNode
        );
    }
}
