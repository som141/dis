package discordgateway.stocknode.quote.service;

import discordgateway.stocknode.bootstrap.StockQuoteProperties;
import discordgateway.stocknode.cache.QuoteRepository;
import discordgateway.stocknode.application.QuoteNotReadyException;
import discordgateway.stocknode.quote.model.StockQuote;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

public class QuoteService {

    private final QuoteRepository quoteRepository;
    private final StockQuoteProperties stockQuoteProperties;
    private final Clock clock;

    public QuoteService(
            QuoteRepository quoteRepository,
            StockQuoteProperties stockQuoteProperties,
            Clock clock
    ) {
        this.quoteRepository = quoteRepository;
        this.stockQuoteProperties = stockQuoteProperties;
        this.clock = clock;
    }

    public StockQuoteResult getQuote(String market, String symbol, QuoteUsage quoteUsage) {
        Instant now = clock.instant();
        StockQuote cachedQuote = quoteRepository.find(market, symbol)
                .orElseThrow(() -> new QuoteNotReadyException(
                        "Current quote is not ready yet for " + StockQuote.normalizeSymbol(symbol) + ". Please try again shortly."
                ));

        boolean fresh = isFresh(cachedQuote, quoteUsage, now);
        return new StockQuoteResult(
                cachedQuote,
                fresh ? QuoteSource.CACHE_FRESH : QuoteSource.CACHE_STALE,
                fresh
        );
    }

    public Optional<StockQuoteResult> findCachedQuote(String market, String symbol, QuoteUsage quoteUsage) {
        Instant now = clock.instant();
        return quoteRepository.find(market, symbol)
                .map(quote -> new StockQuoteResult(
                        quote,
                        isFresh(quote, quoteUsage, now) ? QuoteSource.CACHE_FRESH : QuoteSource.CACHE_STALE,
                        isFresh(quote, quoteUsage, now)
                ));
    }

    private boolean isFresh(StockQuote stockQuote, QuoteUsage quoteUsage, Instant now) {
        Duration maxAge = switch (quoteUsage) {
            case QUERY, RANK -> stockQuoteProperties.getFreshness();
            case TRADE -> stockQuoteProperties.getTradeFreshness();
        };
        return !stockQuote.quotedAt().isBefore(now.minus(maxAge));
    }
}
