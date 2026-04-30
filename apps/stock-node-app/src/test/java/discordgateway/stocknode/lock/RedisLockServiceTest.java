package discordgateway.stocknode.lock;

import discordgateway.stocknode.cache.StockRedisKeyFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisLockServiceTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private RedisLockService redisLockService;

    @BeforeEach
    void setUp() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        redisLockService = new RedisLockService(
                stringRedisTemplate,
                new StockRedisKeyFactory(),
                Duration.ofSeconds(3)
        );
    }

    @Test
    void acquiresQuoteLockWhenRedisReturnsTrue() {
        when(valueOperations.setIfAbsent(eq("stock:quote:lock:US:AAPL"), anyString(), eq(Duration.ofSeconds(3))))
                .thenReturn(true);

        assertThat(redisLockService.tryAcquire("US", "aapl")).isPresent();
    }

    @Test
    void releasesOnlyOwnedLock() {
        QuoteLockHandle quoteLockHandle = new QuoteLockHandle(
                "stock:quote:lock:US:AAPL",
                "owner-token"
        );
        when(valueOperations.get(quoteLockHandle.key())).thenReturn("owner-token");

        redisLockService.release(quoteLockHandle);

        verify(stringRedisTemplate).delete("stock:quote:lock:US:AAPL");
    }
}
