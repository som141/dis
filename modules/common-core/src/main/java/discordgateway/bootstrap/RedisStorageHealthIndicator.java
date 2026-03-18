package discordgateway.bootstrap;

import discordgateway.infrastructure.redis.RedisSupport;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import redis.clients.jedis.Jedis;

@Component
public class RedisStorageHealthIndicator implements HealthIndicator {

    private final AppProperties appProperties;
    private final ObjectProvider<RedisSupport> redisSupportProvider;

    public RedisStorageHealthIndicator(
            AppProperties appProperties,
            ObjectProvider<RedisSupport> redisSupportProvider
    ) {
        this.appProperties = appProperties;
        this.redisSupportProvider = redisSupportProvider;
    }

    @Override
    public Health health() {
        if (!usesRedisStore()) {
            return Health.up()
                    .withDetail("store", "memory")
                    .build();
        }

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

    private boolean usesRedisStore() {
        return "redis".equalsIgnoreCase(appProperties.getStateStore())
                || "redis".equalsIgnoreCase(appProperties.getQueueStore())
                || "redis".equalsIgnoreCase(appProperties.getPlayerStateStore())
                || "redis".equalsIgnoreCase(appProperties.getCommandDedupStore())
                || "redis".equalsIgnoreCase(appProperties.getEventOutboxStore());
    }
}
