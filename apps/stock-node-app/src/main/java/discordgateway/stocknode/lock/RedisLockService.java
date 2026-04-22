package discordgateway.stocknode.lock;

import discordgateway.stocknode.cache.StockRedisKeyFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

public class RedisLockService implements QuoteLockService {

    private final StringRedisTemplate stringRedisTemplate;
    private final StockRedisKeyFactory stockRedisKeyFactory;
    private final Duration lockTtl;

    public RedisLockService(
            StringRedisTemplate stringRedisTemplate,
            StockRedisKeyFactory stockRedisKeyFactory,
            Duration lockTtl
    ) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.stockRedisKeyFactory = stockRedisKeyFactory;
        this.lockTtl = lockTtl;
    }

    @Override
    public Optional<QuoteLockHandle> tryAcquire(String market, String symbol) {
        String key = stockRedisKeyFactory.quoteLockKey(market, symbol);
        String ownerToken = UUID.randomUUID().toString();
        Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(key, ownerToken, lockTtl);
        if (!Boolean.TRUE.equals(locked)) {
            return Optional.empty();
        }
        return Optional.of(new QuoteLockHandle(key, ownerToken));
    }

    @Override
    public void release(QuoteLockHandle quoteLockHandle) {
        String currentOwner = stringRedisTemplate.opsForValue().get(quoteLockHandle.key());
        if (quoteLockHandle.ownerToken().equals(currentOwner)) {
            stringRedisTemplate.delete(quoteLockHandle.key());
        }
    }
}
