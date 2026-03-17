package discordgateway.infrastructure.redis;

import discordgateway.domain.GuildPlayerState;
import discordgateway.domain.GuildStateRepository;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.HashMap;
import java.util.Map;

public class RedisGuildStateRepository implements GuildStateRepository {

    private static final String KEY_PREFIX = "bot:guild:";
    private static final String KEY_SUFFIX = ":player";

    private static final String FIELD_AUTOPLAY = "autoPlay";
    private static final String FIELD_CONNECTED_VOICE_CHANNEL_ID = "connectedVoiceChannelId";

    private final JedisPool jedisPool;

    public RedisGuildStateRepository(JedisPool jedisPool) {
        this.jedisPool = jedisPool;
    }

    @Override
    public GuildPlayerState getOrCreate(long guildId) {
        try (Jedis jedis = jedisPool.getResource()) {
            String key = key(guildId);
            Map<String, String> values = jedis.hgetAll(key);

            GuildPlayerState state = new GuildPlayerState(guildId);

            if (values == null || values.isEmpty()) {
                return state;
            }

            state.setAutoPlay(Boolean.parseBoolean(values.getOrDefault(FIELD_AUTOPLAY, "false")));

            String connectedChannelId = values.get(FIELD_CONNECTED_VOICE_CHANNEL_ID);
            if (connectedChannelId != null && !connectedChannelId.isBlank()) {
                try {
                    state.setConnectedVoiceChannelId(Long.parseLong(connectedChannelId));
                } catch (NumberFormatException ignored) {
                    state.setConnectedVoiceChannelId(null);
                }
            }

            return state;
        }
    }

    @Override
    public void save(GuildPlayerState state) {
        try (Jedis jedis = jedisPool.getResource()) {
            String key = key(state.getGuildId());

            Map<String, String> values = new HashMap<>();
            values.put(FIELD_AUTOPLAY, Boolean.toString(state.isAutoPlay()));

            if (state.getConnectedVoiceChannelId() != null) {
                values.put(FIELD_CONNECTED_VOICE_CHANNEL_ID, Long.toString(state.getConnectedVoiceChannelId()));
            }

            jedis.hset(key, values);

            if (state.getConnectedVoiceChannelId() == null) {
                jedis.hdel(key, FIELD_CONNECTED_VOICE_CHANNEL_ID);
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
}