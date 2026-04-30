package discordgateway.stocknode.application;

import java.math.BigDecimal;

public record PortfolioPositionView(
        String symbol,
        BigDecimal quantity,
        BigDecimal averageCost,
        BigDecimal currentPrice,
        BigDecimal marketValue,
        BigDecimal costBasis,
        BigDecimal profitLoss,
        boolean fresh
) {
}
