package discordgateway.stocknode.quote.service;

import discordgateway.stocknode.bootstrap.StockQuoteProperties;
import discordgateway.stocknode.cache.StockRedisKeyFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProviderRateLimitServiceTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private ProviderRateLimitService providerRateLimitService;

    @BeforeEach
    void setUp() {
        StockQuoteProperties stockQuoteProperties = new StockQuoteProperties();
        stockQuoteProperties.setProviderPerMinuteLimit(2);
        stockQuoteProperties.setProviderPerDayLimit(5);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        providerRateLimitService = new ProviderRateLimitService(
                stringRedisTemplate,
                new StockRedisKeyFactory(),
                stockQuoteProperties
        );
    }

    @Test
    void consumesProviderBudgetAndSetsExpiryOnFirstWrite() {
        Instant now = Instant.parse("2026-04-22T07:05:31Z");
        when(valueOperations.increment("stock:provider:mock:minute:202604220705")).thenReturn(1L);
        when(valueOperations.increment("stock:provider:mock:day:2026-04-22")).thenReturn(1L);

        assertThat(providerRateLimitService.tryConsume("mock", now)).isTrue();
        verify(stringRedisTemplate).expire("stock:provider:mock:minute:202604220705", Duration.ofMinutes(2));
        verify(stringRedisTemplate).expire("stock:provider:mock:day:2026-04-22", Duration.ofDays(2));
    }

    @Test
    void rejectsProviderCallWhenMinuteBudgetIsExceeded() {
        Instant now = Instant.parse("2026-04-22T07:05:31Z");
        when(valueOperations.increment("stock:provider:mock:minute:202604220705")).thenReturn(3L);
        when(valueOperations.increment("stock:provider:mock:day:2026-04-22")).thenReturn(1L);

        assertThat(providerRateLimitService.tryConsume("mock", now)).isFalse();
    }
}
