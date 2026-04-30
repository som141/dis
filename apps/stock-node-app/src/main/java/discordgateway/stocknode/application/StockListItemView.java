package discordgateway.stocknode.application;

import java.math.BigDecimal;

public record StockListItemView(
        int rankNo,
        String market,
        String symbol,
        String name,
        BigDecimal price,
        BigDecimal changeRate,
        boolean quoteReady,
        boolean fresh
) {
}
