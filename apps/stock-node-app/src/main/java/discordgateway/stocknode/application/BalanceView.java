package discordgateway.stocknode.application;

import java.math.BigDecimal;

public record BalanceView(
        Long accountId,
        long guildId,
        long userId,
        BigDecimal cashBalance
) {
}
