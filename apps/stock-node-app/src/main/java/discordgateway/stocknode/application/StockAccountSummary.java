package discordgateway.stocknode.application;

import java.math.BigDecimal;

public record StockAccountSummary(
        Long accountId,
        long guildId,
        long userId,
        BigDecimal cashBalance
) {
}
