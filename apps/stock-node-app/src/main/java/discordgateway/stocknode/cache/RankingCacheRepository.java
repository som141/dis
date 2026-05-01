package discordgateway.stocknode.cache;

import discordgateway.stocknode.application.RankingPeriod;
import discordgateway.stocknode.application.RankingView;

import java.time.Duration;
import java.util.Optional;

public interface RankingCacheRepository {

    Optional<RankingView> find(long guildId, RankingPeriod rankingPeriod, String seasonKey);

    void save(RankingView rankingView, Duration ttl);

    void evictGuild(long guildId, String seasonKey);
}
