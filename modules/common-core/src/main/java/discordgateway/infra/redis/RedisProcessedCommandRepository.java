package discordgateway.infra.redis;

import discordgateway.common.command.CommandResult;
import discordgateway.playback.domain.CommandProcessingStatus;
import discordgateway.playback.domain.ProcessedCommand;
import discordgateway.playback.domain.ProcessedCommandRepository;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.params.SetParams;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class RedisProcessedCommandRepository implements ProcessedCommandRepository {

    private static final String KEY_PREFIX = "bot:command:";
    private static final String KEY_SUFFIX = ":state";

    private final JedisPool jedisPool;

    public RedisProcessedCommandRepository(JedisPool jedisPool) {
        this.jedisPool = jedisPool;
    }

    @Override
    public ProcessedCommand find(String commandId) {
        try (Jedis jedis = jedisPool.getResource()) {
            String raw = jedis.get(key(commandId));
            return raw == null ? null : deserialize(commandId, raw);
        }
    }

    @Override
    public boolean tryStart(String commandId, long ttlMillis) {
        long now = System.currentTimeMillis();
        String payload = serialize(
                new ProcessedCommand(
                        commandId,
                        CommandProcessingStatus.PROCESSING,
                        null,
                        now
                )
        );

        try (Jedis jedis = jedisPool.getResource()) {
            String response = jedis.set(
                    key(commandId),
                    payload,
                    SetParams.setParams().nx().px(ttlMillis)
            );
            return "OK".equalsIgnoreCase(response);
        }
    }

    @Override
    public void complete(String commandId, CommandResult result, long ttlMillis) {
        long now = System.currentTimeMillis();
        ProcessedCommand processedCommand = new ProcessedCommand(
                commandId,
                CommandProcessingStatus.COMPLETED,
                result,
                now
        );

        try (Jedis jedis = jedisPool.getResource()) {
            jedis.psetex(key(commandId), ttlMillis, serialize(processedCommand));
        }
    }

    @Override
    public void remove(String commandId) {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.del(key(commandId));
        }
    }

    private String key(String commandId) {
        return KEY_PREFIX + commandId + KEY_SUFFIX;
    }

    private String serialize(ProcessedCommand command) {
        CommandResult result = command.result();
        return command.status().name() + "|"
                + encode(result != null ? result.message() : "") + "|"
                + (result != null && result.ephemeral()) + "|"
                + command.updatedAtEpochMs();
    }

    private ProcessedCommand deserialize(String commandId, String raw) {
        String[] parts = raw.split("\\|", 4);
        if (parts.length < 4) {
            return new ProcessedCommand(
                    commandId,
                    CommandProcessingStatus.PROCESSING,
                    null,
                    System.currentTimeMillis()
            );
        }

        CommandProcessingStatus status = parseStatus(parts[0]);
        String message = decode(parts[1]);
        boolean ephemeral = Boolean.parseBoolean(parts[2]);
        long updatedAt = parseLong(parts[3], System.currentTimeMillis());

        CommandResult result = status == CommandProcessingStatus.COMPLETED
                ? new CommandResult(message, ephemeral)
                : null;

        return new ProcessedCommand(commandId, status, result, updatedAt);
    }

    private CommandProcessingStatus parseStatus(String raw) {
        try {
            return CommandProcessingStatus.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return CommandProcessingStatus.PROCESSING;
        }
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
