package discordgateway.infrastructure.redis;

import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

public class RedisSupport implements AutoCloseable {

    private final JedisPool jedisPool;

    public RedisSupport() {
        String host = System.getenv().getOrDefault("REDIS_HOST", "localhost");
        int port = Integer.parseInt(System.getenv().getOrDefault("REDIS_PORT", "6379"));
        String password = emptyToNull(System.getenv("REDIS_PASSWORD"));
        int database = Integer.parseInt(System.getenv().getOrDefault("REDIS_DB", "0"));
        int timeoutMillis = Integer.parseInt(System.getenv().getOrDefault("REDIS_TIMEOUT_MILLIS", "2000"));

        DefaultJedisClientConfig config = DefaultJedisClientConfig.builder()
                .password(password)
                .database(database)
                .timeoutMillis(timeoutMillis)
                .clientName("discord-gateway")
                .build();

        this.jedisPool = new JedisPool(new HostAndPort(host, port), config);

        try (Jedis jedis = jedisPool.getResource()) {
            String pong = jedis.ping();
            if (!"PONG".equalsIgnoreCase(pong)) {
                throw new IllegalStateException("Redis ping failed");
            }
        }
    }

    public JedisPool pool() {
        return jedisPool;
    }

    @Override
    public void close() {
        if (jedisPool != null) {
            jedisPool.close();
        }
    }

    private String emptyToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}