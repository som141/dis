package discordgateway.stocknode.application;

import java.math.BigDecimal;
import java.util.List;

public record TradeHistoryView(
        Long accountId,
        long guildId,
        long userId,
        BigDecimal cashBalance,
        List<TradeHistoryEntryView> entries
) {
}
