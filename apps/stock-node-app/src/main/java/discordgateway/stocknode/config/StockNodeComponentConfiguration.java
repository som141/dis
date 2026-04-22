package discordgateway.stocknode.config;

import discordgateway.stocknode.application.StockAccountApplicationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import discordgateway.stocknode.application.StockAccountApplicationService;
import discordgateway.stocknode.application.StockCommandApplicationService;
import discordgateway.stocknode.bootstrap.StockNodeMessagingProperties;
import discordgateway.stocknode.bootstrap.StockQuoteProperties;
import discordgateway.stocknode.bootstrap.StockNodeStorageProperties;
import discordgateway.stocknode.cache.QuoteRepository;
import discordgateway.stocknode.cache.RedisQuoteRepository;
import discordgateway.stocknode.cache.StockRedisKeyFactory;
import discordgateway.stocknode.lock.QuoteLockService;
import discordgateway.stocknode.lock.RedisLockService;
import discordgateway.stocknode.messaging.StockCommandListener;
import discordgateway.stocknode.messaging.StockCommandResultPublisher;
import discordgateway.stocknode.persistence.repository.StockAccountRepository;
import discordgateway.stocknode.quote.provider.MockQuoteProvider;
import discordgateway.stocknode.quote.provider.QuoteProvider;
import discordgateway.stocknode.quote.service.ProviderRateLimitService;
import discordgateway.stocknode.quote.service.ProviderRateLimiter;
import discordgateway.stocknode.quote.service.QuoteService;
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
    public StockCommandApplicationService stockCommandApplicationService() {
        return new StockCommandApplicationService();
    }

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
    public StockCommandResultPublisher stockCommandResultPublisher() {
        return new StockCommandResultPublisher();
    }

    @Bean
    public StockCommandListener stockCommandListener(
            StockCommandApplicationService stockCommandApplicationService,
            StockCommandResultPublisher stockCommandResultPublisher
    ) {
        return new StockCommandListener(
                stockCommandApplicationService,
                stockCommandResultPublisher
        );
    }
}
