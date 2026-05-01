package discordgateway.stocknode.application;

import java.time.Instant;
import java.util.List;

public record RankingView(
        long guildId,
        String seasonKey,
        String period,
        Instant generatedAt,
        List<RankingEntryView> entries
) {
}
