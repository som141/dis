package discordgateway.stocknode.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import discordgateway.stocknode.application.BalanceQueryService;
import discordgateway.stocknode.application.DailyAllowanceService;
import discordgateway.stocknode.application.PortfolioQueryService;
import discordgateway.stocknode.application.PortfolioService;
import discordgateway.stocknode.application.StockAccountApplicationService;
import discordgateway.stocknode.application.StockCommandApplicationService;
import discordgateway.stocknode.application.StockResponseFormatter;
import discordgateway.stocknode.application.TradeExecutionService;
import discordgateway.stocknode.application.TradeHistoryQueryService;
import discordgateway.stocknode.bootstrap.StockNodeMessagingProperties;
import discordgateway.stocknode.bootstrap.StockNodeStorageProperties;
import discordgateway.stocknode.bootstrap.StockQuoteProperties;
import discordgateway.stocknode.cache.QuoteRepository;
import discordgateway.stocknode.cache.RedisQuoteRepository;
import discordgateway.stocknode.cache.StockRedisKeyFactory;
import discordgateway.stocknode.lock.QuoteLockService;
import discordgateway.stocknode.lock.RedisLockService;
import discordgateway.stocknode.persistence.repository.AllowanceLedgerRepository;
import discordgateway.stocknode.persistence.repository.StockAccountRepository;
import discordgateway.stocknode.persistence.repository.StockPositionRepository;
import discordgateway.stocknode.persistence.repository.TradeLedgerRepository;
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
        StockNodeMessagingProperties.class,
        StockNodeStorageProperties.class,
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
    public QuoteProvider quoteProvider(Clock stockClock) {
        return new MockQuoteProvider(stockClock);
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
            Clock stockClock
    ) {
        return new DailyAllowanceService(
                stockAccountApplicationService,
                stockAccountRepository,
                allowanceLedgerRepository,
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
            StockQuoteProperties stockQuoteProperties,
            Clock stockClock
    ) {
        return new TradeExecutionService(
                dailyAllowanceService,
                stockAccountRepository,
                stockPositionRepository,
                tradeLedgerRepository,
                quoteService,
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
                stockResponseFormatter,
                stockQuoteProperties,
                stockClock,
                producerNode
        );
    }
}
