package discordgateway.infra.redis;

import discordgateway.playback.domain.QueueEntry;
import discordgateway.playback.domain.QueueRepository;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class RedisQueueRepository implements QueueRepository {

    private static final String KEY_PREFIX = "bot:guild:";
    private static final String KEY_SUFFIX = ":queue";

    private final JedisPool jedisPool;

    public RedisQueueRepository(JedisPool jedisPool) {
        this.jedisPool = jedisPool;
    }

    @Override
    public void push(long guildId, QueueEntry entry) {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.rpush(key(guildId), serialize(entry));
        }
    }

    @Override
    public QueueEntry poll(long guildId) {
        try (Jedis jedis = jedisPool.getResource()) {
            String raw = jedis.lpop(key(guildId));
            return raw == null ? null : deserialize(raw);
        }
    }

    @Override
    public boolean hasEntries(long guildId) {
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.llen(key(guildId)) > 0;
        }
    }

    @Override
    public List<QueueEntry> list(long guildId, int limit) {
        try (Jedis jedis = jedisPool.getResource()) {
            List<String> rawList = jedis.lrange(key(guildId), 0, Math.max(0, limit - 1));
            List<QueueEntry> result = new ArrayList<>();

            for (String raw : rawList) {
                result.add(deserialize(raw));
            }
            return result;
        }
    }

    @Override
    public void clear(long guildId) {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.del(key(guildId));
        }
    }

    private String key(long guildId) {
        return KEY_PREFIX + guildId + KEY_SUFFIX;
    }

    private String serialize(QueueEntry entry) {
        return encode(entry.identifier()) + "|"
                + encode(entry.title()) + "|"
                + encode(entry.author()) + "|"
                + entry.requestedAtMillis();
    }

    private QueueEntry deserialize(String raw) {
        String[] parts = raw.split("\\|", 4);
        if (parts.length < 4) {
            return new QueueEntry("unknown", "unknown", "unknown", System.currentTimeMillis());
        }

        return new QueueEntry(
                decode(parts[0]),
                decode(parts[1]),
                decode(parts[2]),
                parseLong(parts[3], System.currentTimeMillis())
        );
    }

    private String encode(String value) {
        String safe = value == null ? "" : value;
        return Base64.getUrlEncoder().encodeToString(safe.getBytes(StandardCharsets.UTF_8));
    }

    private String decode(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }

    private long parseLong(String raw, long defaultValue) {
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
