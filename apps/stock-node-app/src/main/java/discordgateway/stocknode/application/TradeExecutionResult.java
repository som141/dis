package discordgateway.stocknode.application;

import java.math.BigDecimal;

public record TradeExecutionResult(
        Long accountId,
        long guildId,
        long userId,
        TradeSide side,
        String market,
        String symbol,
        BigDecimal requestedQuantity,
        int leverage,
        BigDecimal marginAmount,
        BigDecimal notionalAmount,
        BigDecimal executedQuantity,
        BigDecimal unitPrice,
        BigDecimal settledAmount,
        BigDecimal remainingCash,
        BigDecimal remainingPositionQuantity,
        BigDecimal remainingPositionAverageCost,
        String warningMessage
) {
}
