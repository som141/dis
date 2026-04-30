package discordgateway.stocknode.application;

import java.math.BigDecimal;

public record RankingEntryView(
        long accountId,
        long userId,
        BigDecimal totalEquity,
        BigDecimal baselineEquity,
        BigDecimal returnRatePercent
) {
}
