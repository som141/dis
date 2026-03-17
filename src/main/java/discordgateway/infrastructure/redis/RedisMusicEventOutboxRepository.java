package discordgateway.infrastructure.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import discordgateway.application.event.MusicEvent;
import discordgateway.domain.MusicEventOutboxRepository;
import discordgateway.domain.PendingMusicEvent;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class RedisMusicEventOutboxRepository implements MusicEventOutboxRepository {

    private static final String DUE_SET_KEY = "bot:event:outbox:due";
    private static final String ITEM_KEY_PREFIX = "bot:event:outbox:item:";
    private static final String CLAIM_DUE_SCRIPT = """
            local dueSetKey = KEYS[1]
            local now = tonumber(ARGV[1])
            local leaseUntil = tonumber(ARGV[2])
            local claimerId = ARGV[3]
            local claimToken = ARGV[4]
            local itemKeyPrefix = ARGV[5]
            local scanLimit = tonumber(ARGV[6])

            local ids = redis.call('ZRANGEBYSCORE', dueSetKey, '-inf', now, 'LIMIT', 0, scanLimit)
            for _, eventId in ipairs(ids) do
                local itemKey = itemKeyPrefix .. eventId
                if redis.call('EXISTS', itemKey) == 0 then
                    redis.call('ZREM', dueSetKey, eventId)
                else
                    local nextAttemptAt = tonumber(redis.call('HGET', itemKey, 'nextAttemptAtEpochMs') or '0')
                    local claimUntil = tonumber(redis.call('HGET', itemKey, 'claimUntilEpochMs') or '0')
                    if nextAttemptAt <= now and claimUntil <= now then
                        redis.call(
                            'HSET',
                            itemKey,
                            'claimOwner', claimerId,
                            'claimToken', claimToken,
                            'claimUntilEpochMs', tostring(leaseUntil)
                        )
                        redis.call('ZADD', dueSetKey, leaseUntil, eventId)
                        return eventId
                    end

                    local visibleAt = nextAttemptAt
                    if claimUntil > visibleAt then
                        visibleAt = claimUntil
                    end
                    redis.call('ZADD', dueSetKey, visibleAt, eventId)
                end
            end

            return ''
            """;
    private static final String MARK_SUCCEEDED_SCRIPT = """
            local itemKey = KEYS[1]
            local dueSetKey = KEYS[2]
            local claimToken = ARGV[1]
            local eventId = ARGV[2]

            local storedClaimToken = redis.call('HGET', itemKey, 'claimToken')
            if (not storedClaimToken) or storedClaimToken ~= claimToken then
                return 0
            end

            redis.call('DEL', itemKey)
            redis.call('ZREM', dueSetKey, eventId)
            return 1
            """;
    private static final String RESCHEDULE_SCRIPT = """
            local itemKey = KEYS[1]
            local dueSetKey = KEYS[2]
            local claimToken = ARGV[1]
            local eventId = ARGV[2]
            local nextAttemptCount = ARGV[3]
            local nextAttemptAtEpochMs = ARGV[4]
            local lastError = ARGV[5]

            local storedClaimToken = redis.call('HGET', itemKey, 'claimToken')
            if (not storedClaimToken) or storedClaimToken ~= claimToken then
                return 0
            end

            redis.call(
                'HSET',
                itemKey,
                'attemptCount', nextAttemptCount,
                'nextAttemptAtEpochMs', nextAttemptAtEpochMs,
                'lastError', lastError,
                'claimOwner', '',
                'claimToken', '',
                'claimUntilEpochMs', '0'
            )
            redis.call('ZADD', dueSetKey, tonumber(nextAttemptAtEpochMs), eventId)
            return 1
            """;

    private final JedisPool jedisPool;
    private final ObjectMapper objectMapper;

    public RedisMusicEventOutboxRepository(JedisPool jedisPool, ObjectMapper objectMapper) {
        this.jedisPool = jedisPool;
        this.objectMapper = objectMapper;
    }

    @Override
    public void saveNew(MusicEvent event, long nextAttemptAtEpochMs, String lastError) {
        try (Jedis jedis = jedisPool.getResource()) {
            String itemKey = itemKey(event.eventId());
            if (jedis.exists(itemKey)) {
                return;
            }

            jedis.hset(
                    itemKey,
                    toHash(
                            objectMapper.writeValueAsString(event),
                            1,
                            nextAttemptAtEpochMs,
                            lastError,
                            null,
                            null,
                            0L
                    )
            );
            jedis.zadd(DUE_SET_KEY, nextAttemptAtEpochMs, event.eventId());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize outbox event " + event.eventId(), e);
        }
    }

    @Override
    public List<PendingMusicEvent> claimDue(int limit, long nowEpochMs, String claimerId, long claimTtlMs) {
        try (Jedis jedis = jedisPool.getResource()) {
            List<PendingMusicEvent> result = new ArrayList<>();
            int scanLimit = Math.max(limit * 2, 10);
            long leaseUntil = nowEpochMs + claimTtlMs;

            for (int i = 0; i < limit; i++) {
                String claimToken = UUID.randomUUID().toString();
                Object raw = jedis.eval(
                        CLAIM_DUE_SCRIPT,
                        Collections.singletonList(DUE_SET_KEY),
                        List.of(
                                Long.toString(nowEpochMs),
                                Long.toString(leaseUntil),
                                claimerId,
                                claimToken,
                                ITEM_KEY_PREFIX,
                                Integer.toString(scanLimit)
                        )
                );
                String eventId = raw instanceof byte[] ? new String((byte[]) raw) : raw != null ? raw.toString() : "";
                if (eventId == null || eventId.isBlank()) {
                    break;
                }

                PendingMusicEvent event = findById(jedis, eventId);
                if (event != null) {
                    result.add(event);
                }
            }

            return result;
        }
    }

    @Override
    public boolean markSucceeded(String eventId, String claimToken) {
        try (Jedis jedis = jedisPool.getResource()) {
            Object raw = jedis.eval(
                    MARK_SUCCEEDED_SCRIPT,
                    List.of(itemKey(eventId), DUE_SET_KEY),
                    List.of(claimToken, eventId)
            );
            return raw != null && Long.parseLong(raw.toString()) == 1L;
        }
    }

    @Override
    public boolean reschedule(
            String eventId,
            String claimToken,
            int nextAttemptCount,
            long nextAttemptAtEpochMs,
            String lastError
    ) {
        try (Jedis jedis = jedisPool.getResource()) {
            Object raw = jedis.eval(
                    RESCHEDULE_SCRIPT,
                    List.of(itemKey(eventId), DUE_SET_KEY),
                    List.of(
                            claimToken,
                            eventId,
                            Integer.toString(nextAttemptCount),
                            Long.toString(nextAttemptAtEpochMs),
                            safe(lastError)
                    )
            );
            return raw != null && Long.parseLong(raw.toString()) == 1L;
        }
    }

    private PendingMusicEvent findById(Jedis jedis, String eventId) {
        var data = jedis.hgetAll(itemKey(eventId));
        if (data == null || data.isEmpty()) {
            jedis.zrem(DUE_SET_KEY, eventId);
            return null;
        }

        try {
            MusicEvent event = objectMapper.readValue(data.get("payload"), MusicEvent.class);
            return new PendingMusicEvent(
                    eventId,
                    event,
                    parseInt(data.get("attemptCount"), 1),
                    parseLong(data.get("nextAttemptAtEpochMs"), System.currentTimeMillis()),
                    emptyToNull(data.get("lastError")),
                    emptyToNull(data.get("claimOwner")),
                    emptyToNull(data.get("claimToken")),
                    parseLong(data.get("claimUntilEpochMs"), 0L)
            );
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize outbox event " + eventId, e);
        }
    }

    private String itemKey(String eventId) {
        return ITEM_KEY_PREFIX + eventId;
    }

    private java.util.Map<String, String> toHash(
            String payload,
            int attemptCount,
            long nextAttemptAtEpochMs,
            String lastError,
            String claimOwner,
            String claimToken,
            long claimUntilEpochMs
    ) {
        java.util.Map<String, String> map = new java.util.HashMap<>();
        map.put("payload", payload);
        map.put("attemptCount", Integer.toString(attemptCount));
        map.put("nextAttemptAtEpochMs", Long.toString(nextAttemptAtEpochMs));
        map.put("lastError", safe(lastError));
        map.put("claimOwner", safe(claimOwner));
        map.put("claimToken", safe(claimToken));
        map.put("claimUntilEpochMs", Long.toString(claimUntilEpochMs));
        return map;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private int parseInt(String raw, int defaultValue) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private long parseLong(String raw, long defaultValue) {
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
