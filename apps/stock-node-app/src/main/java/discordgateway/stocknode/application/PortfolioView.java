package discordgateway.stocknode.application;

import java.math.BigDecimal;
import java.util.List;

public record PortfolioView(
        Long accountId,
        long guildId,
        long userId,
        BigDecimal cashBalance,
        BigDecimal totalMarketValue,
        BigDecimal totalCostBasis,
        BigDecimal totalEquity,
        BigDecimal totalProfitLoss,
        List<PortfolioPositionView> positions
) {
}
