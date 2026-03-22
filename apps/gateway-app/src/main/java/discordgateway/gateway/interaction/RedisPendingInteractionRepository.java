package discordgateway.gateway.interaction;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

public class RedisPendingInteractionRepository implements PendingInteractionRepository {

    private static final Logger log = LoggerFactory.getLogger(RedisPendingInteractionRepository.class);
    private static final String KEY_PREFIX = "gateway:pending-interaction:";
    private static final String GET_DELETE_SCRIPT = """
            local value = redis.call('GET', KEYS[1])
            if value then
              redis.call('DEL', KEYS[1])
            end
            return value
            """;

    private final JedisPool jedisPool;
    private final ObjectMapper objectMapper;

    public RedisPendingInteractionRepository(JedisPool jedisPool, ObjectMapper objectMapper) {
        this.jedisPool = jedisPool;
        this.objectMapper = objectMapper;
    }

    @Override
    public void put(String commandId, InteractionResponseContext context) {
        long ttlMs = context.expiresAtEpochMs() - System.currentTimeMillis();
        if (ttlMs <= 0) {
            remove(commandId);
            return;
        }

        try (Jedis jedis = jedisPool.getResource()) {
            String json = objectMapper.writeValueAsString(context);
            jedis.psetex(key(commandId), ttlMs, json);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to store pending interaction. commandId=" + commandId, e);
        }
    }

    @Override
    public InteractionResponseContext take(String commandId) {
        try (Jedis jedis = jedisPool.getResource()) {
            Object raw = jedis.eval(GET_DELETE_SCRIPT, 1, key(commandId));
            if (raw == null) {
                return null;
            }

            InteractionResponseContext context = objectMapper.readValue(raw.toString(), InteractionResponseContext.class);
            if (context.isExpired(System.currentTimeMillis())) {
                log.atInfo()
                        .addKeyValue("commandId", commandId)
                        .addKeyValue("commandName", context.commandName())
                        .addKeyValue("guildId", context.guildId())
                        .log("pending interaction expired before result consumption");
                return null;
            }
            return context;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to consume pending interaction. commandId=" + commandId, e);
        }
    }

    @Override
    public void remove(String commandId) {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.del(key(commandId));
        }
    }

    private String key(String commandId) {
        return KEY_PREFIX + commandId;
    }
}
