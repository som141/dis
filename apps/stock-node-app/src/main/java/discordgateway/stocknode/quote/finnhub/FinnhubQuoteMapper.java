package discordgateway.stocknode.quote.finnhub;

import discordgateway.stocknode.persistence.entity.StockWatchlistEntity;
import discordgateway.stocknode.quote.model.StockQuote;

import java.time.Clock;

public class FinnhubQuoteMapper {

    private final Clock clock;

    public FinnhubQuoteMapper(Clock clock) {
        this.clock = clock;
    }

    public StockQuote map(StockWatchlistEntity watchlistItem, FinnhubQuoteResponse response) {
        return new StockQuote(
                watchlistItem.normalizedMarket(),
                watchlistItem.normalizedSymbol(),
                response.currentPrice(),
                clock.instant(),
                "finnhub",
                response.changeAmount(),
                response.changeRate(),
                response.high(),
                response.low(),
                response.open(),
                response.previousClose()
        );
    }
}
