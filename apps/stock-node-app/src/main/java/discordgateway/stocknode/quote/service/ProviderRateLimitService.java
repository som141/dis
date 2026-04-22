package discordgateway.stocknode.quote.service;

import discordgateway.stocknode.bootstrap.StockQuoteProperties;
import discordgateway.stocknode.cache.StockRedisKeyFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

public class ProviderRateLimitService implements ProviderRateLimiter {

    private static final Duration MINUTE_BUCKET_TTL = Duration.ofMinutes(2);
    private static final Duration DAY_BUCKET_TTL = Duration.ofDays(2);

    private final StringRedisTemplate stringRedisTemplate;
    private final StockRedisKeyFactory stockRedisKeyFactory;
    private final StockQuoteProperties stockQuoteProperties;

    public ProviderRateLimitService(
            StringRedisTemplate stringRedisTemplate,
            StockRedisKeyFactory stockRedisKeyFactory,
            StockQuoteProperties stockQuoteProperties
    ) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.stockRedisKeyFactory = stockRedisKeyFactory;
        this.stockQuoteProperties = stockQuoteProperties;
    }

    @Override
    public boolean tryConsume(String provider, Instant now) {
        String minuteKey = stockRedisKeyFactory.providerMinuteLimitKey(provider, now);
        String dayKey = stockRedisKeyFactory.providerDayLimitKey(provider, now.atZone(ZoneOffset.UTC).toLocalDate());

        long minuteCount = incrementWithTtl(minuteKey, MINUTE_BUCKET_TTL);
        long dayCount = incrementWithTtl(dayKey, DAY_BUCKET_TTL);

        return minuteCount <= stockQuoteProperties.getProviderPerMinuteLimit()
                && dayCount <= stockQuoteProperties.getProviderPerDayLimit();
    }

    private long incrementWithTtl(String key, Duration ttl) {
        Long updatedCount = stringRedisTemplate.opsForValue().increment(key);
        if (updatedCount == null) {
            return Long.MAX_VALUE;
        }
        if (updatedCount == 1L) {
            stringRedisTemplate.expire(key, ttl);
        }
        return updatedCount;
    }
}
