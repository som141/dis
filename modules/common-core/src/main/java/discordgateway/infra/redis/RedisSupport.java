package discordgateway.infra.redis;

import discordgateway.common.bootstrap.RedisConnectionProperties;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

public class RedisSupport implements AutoCloseable {

    private final JedisPool jedisPool;

    public RedisSupport(RedisConnectionProperties properties) {
        String host = properties.getHost();
        int port = properties.getPort();
        String password = emptyToNull(properties.getPassword());
        int database = properties.getDb();
        int timeoutMillis = properties.getTimeoutMillis();

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
