package discordgateway.infra.redis;

import discordgateway.playback.domain.PlayerState;
import discordgateway.playback.domain.PlayerStateRepository;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.HashMap;
import java.util.Map;

public class RedisPlayerStateRepository implements PlayerStateRepository {

    private static final String KEY_PREFIX = "bot:guild:";
    private static final String KEY_SUFFIX = ":player";

    private static final String FIELD_NOW_PLAYING = "nowPlaying";
    private static final String FIELD_PAUSED = "paused";
    private static final String FIELD_AUTOPLAY = "autoPlay";
    private static final String FIELD_REPEAT_MODE = "repeatMode";
    private static final String FIELD_OWNER_NODE = "ownerNode";
    private static final String FIELD_PROCESSING_FLAG = "processingFlag";

    private final JedisPool jedisPool;

    public RedisPlayerStateRepository(JedisPool jedisPool) {
        this.jedisPool = jedisPool;
    }

    @Override
    public PlayerState getOrCreate(long guildId) {
        try (Jedis jedis = jedisPool.getResource()) {
            Map<String, String> values = jedis.hgetAll(key(guildId));
            PlayerState state = new PlayerState(guildId);

            if (values == null || values.isEmpty()) {
                return state;
            }

            state.setNowPlaying(blankToNull(values.get(FIELD_NOW_PLAYING)));
            state.setPaused(Boolean.parseBoolean(values.getOrDefault(FIELD_PAUSED, "false")));
            state.setAutoPlay(Boolean.parseBoolean(values.getOrDefault(FIELD_AUTOPLAY, "false")));
            state.setRepeatMode(values.get(FIELD_REPEAT_MODE));
            state.setOwnerNode(blankToNull(values.get(FIELD_OWNER_NODE)));
            state.setProcessingFlag(Boolean.parseBoolean(values.getOrDefault(FIELD_PROCESSING_FLAG, "false")));
            return state;
        }
    }

    @Override
    public void save(PlayerState state) {
        try (Jedis jedis = jedisPool.getResource()) {
            String key = key(state.getGuildId());
            Map<String, String> values = new HashMap<>();

            values.put(FIELD_PAUSED, Boolean.toString(state.isPaused()));
            values.put(FIELD_AUTOPLAY, Boolean.toString(state.isAutoPlay()));
            values.put(FIELD_REPEAT_MODE, state.getRepeatMode());
            values.put(FIELD_PROCESSING_FLAG, Boolean.toString(state.isProcessingFlag()));

            if (state.getNowPlaying() != null && !state.getNowPlaying().isBlank()) {
                values.put(FIELD_NOW_PLAYING, state.getNowPlaying());
            }

            if (state.getOwnerNode() != null && !state.getOwnerNode().isBlank()) {
                values.put(FIELD_OWNER_NODE, state.getOwnerNode());
            }

            jedis.hset(key, values);

            if (state.getNowPlaying() == null || state.getNowPlaying().isBlank()) {
                jedis.hdel(key, FIELD_NOW_PLAYING);
            }

            if (state.getOwnerNode() == null || state.getOwnerNode().isBlank()) {
                jedis.hdel(key, FIELD_OWNER_NODE);
            }
        }
    }

    @Override
    public void remove(long guildId) {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.del(key(guildId));
        }
    }

    private String key(long guildId) {
        return KEY_PREFIX + guildId + KEY_SUFFIX;
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }
}
