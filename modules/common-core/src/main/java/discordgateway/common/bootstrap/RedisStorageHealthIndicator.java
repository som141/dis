package discordgateway.common.bootstrap;

import discordgateway.infra.redis.RedisSupport;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import redis.clients.jedis.Jedis;

@Component
public class RedisStorageHealthIndicator implements HealthIndicator {

    private final ObjectProvider<RedisSupport> redisSupportProvider;

    public RedisStorageHealthIndicator(ObjectProvider<RedisSupport> redisSupportProvider) {
        this.redisSupportProvider = redisSupportProvider;
    }

    @Override
    public Health health() {
        RedisSupport redisSupport = redisSupportProvider.getIfAvailable();
        if (redisSupport == null) {
            return Health.down()
                    .withDetail("store", "redis")
                    .withDetail("reason", "redis_support_missing")
                    .build();
        }

        try (Jedis jedis = redisSupport.pool().getResource()) {
            String pong = jedis.ping();
            if (!"PONG".equalsIgnoreCase(pong)) {
                return Health.down()
                        .withDetail("store", "redis")
                        .withDetail("pong", pong)
                        .build();
            }
        } catch (Exception e) {
            return Health.down(e)
                    .withDetail("store", "redis")
                    .build();
        }

        return Health.up()
                .withDetail("store", "redis")
                .build();
    }
}
