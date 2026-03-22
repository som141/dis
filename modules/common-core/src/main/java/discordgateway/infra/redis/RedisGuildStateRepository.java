package discordgateway.infra.redis;

import discordgateway.playback.domain.GuildPlayerState;
import discordgateway.playback.domain.GuildStateRepository;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.HashMap;
import java.util.Map;

public class RedisGuildStateRepository implements GuildStateRepository {

    private static final String KEY_PREFIX = "bot:guild:";
    private static final String KEY_SUFFIX = ":player";

    private static final String FIELD_CONNECTED_VOICE_CHANNEL_ID = "connectedVoiceChannelId";
    private static final String FIELD_LAST_TEXT_CHANNEL_ID = "lastTextChannelId";

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

            String connectedChannelId = values.get(FIELD_CONNECTED_VOICE_CHANNEL_ID);
            if (connectedChannelId != null && !connectedChannelId.isBlank()) {
                try {
                    state.setConnectedVoiceChannelId(Long.parseLong(connectedChannelId));
                } catch (NumberFormatException ignored) {
                    state.setConnectedVoiceChannelId(null);
                }
            }

            String lastTextChannelId = values.get(FIELD_LAST_TEXT_CHANNEL_ID);
            if (lastTextChannelId != null && !lastTextChannelId.isBlank()) {
                try {
                    state.setLastTextChannelId(Long.parseLong(lastTextChannelId));
                } catch (NumberFormatException ignored) {
                    state.setLastTextChannelId(null);
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

            if (state.getConnectedVoiceChannelId() != null) {
                values.put(FIELD_CONNECTED_VOICE_CHANNEL_ID, Long.toString(state.getConnectedVoiceChannelId()));
            }

            if (state.getLastTextChannelId() != null) {
                values.put(FIELD_LAST_TEXT_CHANNEL_ID, Long.toString(state.getLastTextChannelId()));
            }

            if (!values.isEmpty()) {
                jedis.hset(key, values);
            }

            if (state.getConnectedVoiceChannelId() == null) {
                jedis.hdel(key, FIELD_CONNECTED_VOICE_CHANNEL_ID);
            }

            if (state.getLastTextChannelId() == null) {
                jedis.hdel(key, FIELD_LAST_TEXT_CHANNEL_ID);
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
