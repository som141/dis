package discordgateway.stocknode.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import discordgateway.stocknode.application.RankingPeriod;
import discordgateway.stocknode.application.RankingView;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Optional;

public class RedisRankingCacheRepository implements RankingCacheRepository {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final StockRedisKeyFactory stockRedisKeyFactory;

    public RedisRankingCacheRepository(
            StringRedisTemplate stringRedisTemplate,
            ObjectMapper objectMapper,
            StockRedisKeyFactory stockRedisKeyFactory
    ) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.stockRedisKeyFactory = stockRedisKeyFactory;
    }

    @Override
    public Optional<RankingView> find(long guildId, RankingPeriod rankingPeriod, String seasonKey) {
        try {
            String raw = stringRedisTemplate.opsForValue()
                    .get(stockRedisKeyFactory.rankKey(guildId, rankingPeriod.name(), seasonKey));
            if (raw == null || raw.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(raw, RankingView.class));
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to read ranking cache", exception);
        }
    }

    @Override
    public void save(RankingView rankingView, Duration ttl) {
        try {
            stringRedisTemplate.opsForValue().set(
                    stockRedisKeyFactory.rankKey(rankingView.guildId(), rankingView.period(), rankingView.seasonKey()),
                    objectMapper.writeValueAsString(rankingView),
                    ttl
            );
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to store ranking cache", exception);
        }
    }

    @Override
    public void evictGuild(long guildId, String seasonKey) {
        for (discordgateway.stocknode.application.RankingPeriod rankingPeriod
                : discordgateway.stocknode.application.RankingPeriod.values()) {
            stringRedisTemplate.delete(stockRedisKeyFactory.rankKey(guildId, rankingPeriod.name(), seasonKey));
        }
    }
}
