package discordgateway.stocknode.application;

import java.math.BigDecimal;
import java.time.Instant;

public record TradeHistoryEntryView(
        String symbol,
        TradeSide side,
        BigDecimal quantity,
        BigDecimal unitPrice,
        int leverage,
        BigDecimal marginAmount,
        BigDecimal notionalAmount,
        Instant occurredAt
) {
}
