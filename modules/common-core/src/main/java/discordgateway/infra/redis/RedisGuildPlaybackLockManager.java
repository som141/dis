package discordgateway.infra.redis;

import discordgateway.playback.domain.GuildPlaybackLockManager;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.params.SetParams;

import java.util.Collections;
import java.util.UUID;

public class RedisGuildPlaybackLockManager implements GuildPlaybackLockManager {

    private static final String KEY_PREFIX = "bot:guild:";
    private static final String KEY_SUFFIX = ":lock";
    private static final String RELEASE_SCRIPT =
            "if redis.call('get', KEYS[1]) == ARGV[1] then "
                    + "return redis.call('del', KEYS[1]) "
                    + "else return 0 end";

    private final JedisPool jedisPool;
    private final long ttlMillis;

    public RedisGuildPlaybackLockManager(JedisPool jedisPool, long ttlMillis) {
        this.jedisPool = jedisPool;
        this.ttlMillis = ttlMillis;
    }

    @Override
    public GuildPlaybackLock tryAcquire(long guildId) {
        String key = key(guildId);
        String token = UUID.randomUUID().toString();

        try (Jedis jedis = jedisPool.getResource()) {
            String result = jedis.set(key, token, SetParams.setParams().nx().px(ttlMillis));
            if (!"OK".equalsIgnoreCase(result)) {
                return new FailedLock();
            }
        }

        return new HeldLock(jedisPool, key, token);
    }

    private String key(long guildId) {
        return KEY_PREFIX + guildId + KEY_SUFFIX;
    }

    private static final class HeldLock implements GuildPlaybackLock {
        private final JedisPool jedisPool;
        private final String key;
        private final String token;
        private boolean released;

        private HeldLock(JedisPool jedisPool, String key, String token) {
            this.jedisPool = jedisPool;
            this.key = key;
            this.token = token;
        }

        @Override
        public boolean acquired() {
            return true;
        }

        @Override
        public void release() {
            if (released) {
                return;
            }
            released = true;

            try (Jedis jedis = jedisPool.getResource()) {
                jedis.eval(RELEASE_SCRIPT, Collections.singletonList(key), Collections.singletonList(token));
            }
        }
    }

    private static final class FailedLock implements GuildPlaybackLock {
        @Override
        public boolean acquired() {
            return false;
        }

        @Override
        public void release() {
        }
    }
}
