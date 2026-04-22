package discordgateway.stocknode.cache;

import discordgateway.stocknode.quote.model.StockQuote;

import java.time.Duration;
import java.util.Optional;

public interface QuoteRepository {

    Optional<StockQuote> find(String market, String symbol);

    void save(StockQuote quote, Duration ttl);
}
