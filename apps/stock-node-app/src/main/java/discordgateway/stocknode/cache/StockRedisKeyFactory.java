package discordgateway.stocknode.cache;

import discordgateway.stocknode.quote.model.StockQuote;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;

public class StockRedisKeyFactory {

    private static final DateTimeFormatter PROVIDER_MINUTE_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmm").withZone(ZoneOffset.UTC);

    public String quoteKey(String market, String symbol) {
        return "stock:quote:" + normalizeMarket(market) + ":" + normalizeSymbol(symbol);
    }

    public String quoteLockKey(String market, String symbol) {
        return "stock:quote:lock:" + normalizeMarket(market) + ":" + normalizeSymbol(symbol);
    }

    public String providerMinuteLimitKey(String provider, Instant timestamp) {
        return "stock:provider:" + normalizeProvider(provider) + ":minute:" + PROVIDER_MINUTE_FORMAT.format(timestamp);
    }

    public String providerDayLimitKey(String provider, LocalDate date) {
        return "stock:provider:" + normalizeProvider(provider) + ":day:" + Objects.requireNonNull(date, "date");
    }

    public String rankKey(long guildId, String period) {
        return "stock:rank:" + guildId + ":" + Objects.requireNonNull(period, "period").trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeMarket(String market) {
        return Objects.requireNonNull(market, "market").trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeSymbol(String symbol) {
        return StockQuote.normalizeSymbol(symbol);
    }

    private String normalizeProvider(String provider) {
        return Objects.requireNonNull(provider, "provider").trim().toLowerCase(Locale.ROOT);
    }
}
