package discordgateway.stocknode.quote.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

public record StockQuote(
        String market,
        String symbol,
        BigDecimal price,
        Instant quotedAt
) {

    public StockQuote {
        market = normalizeMarket(market);
        symbol = normalizeSymbol(symbol);
        price = Objects.requireNonNull(price, "price");
        quotedAt = Objects.requireNonNull(quotedAt, "quotedAt");
    }

    public static String normalizeMarket(String market) {
        return Objects.requireNonNull(market, "market").trim().toLowerCase(Locale.ROOT);
    }

    public static String normalizeSymbol(String symbol) {
        return Objects.requireNonNull(symbol, "symbol").trim().toUpperCase(Locale.ROOT);
    }
}
