package discordgateway.stocknode.quote.service;

import discordgateway.stocknode.bootstrap.StockQuoteProperties;
import discordgateway.stocknode.cache.QuoteRepository;
import discordgateway.stocknode.lock.QuoteLockHandle;
import discordgateway.stocknode.lock.QuoteLockService;
import discordgateway.stocknode.quote.model.StockQuote;
import discordgateway.stocknode.quote.provider.QuoteProvider;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

public class MarketQuoteRefreshService {

    private final QuoteRepository quoteRepository;
    private final QuoteLockService quoteLockService;
    private final QuoteProvider quoteProvider;
    private final ProviderRateLimiter providerRateLimiter;
    private final StockQuoteProperties stockQuoteProperties;
    private final Clock clock;

    public MarketQuoteRefreshService(
            QuoteRepository quoteRepository,
            QuoteLockService quoteLockService,
            QuoteProvider quoteProvider,
            ProviderRateLimiter providerRateLimiter,
            StockQuoteProperties stockQuoteProperties,
            Clock clock
    ) {
        this.quoteRepository = quoteRepository;
        this.quoteLockService = quoteLockService;
        this.quoteProvider = quoteProvider;
        this.providerRateLimiter = providerRateLimiter;
        this.stockQuoteProperties = stockQuoteProperties;
        this.clock = clock;
    }

    public StockQuote refreshQuote(String market, String symbol) {
        Instant now = clock.instant();
        Optional<QuoteLockHandle> lockHandle = quoteLockService.tryAcquire(market, symbol);
        if (lockHandle.isEmpty()) {
            return quoteRepository.find(market, symbol)
                    .orElseThrow(() -> new IllegalStateException("Quote refresh already in progress for " + symbol));
        }

        try {
            if (!providerRateLimiter.tryConsume(quoteProvider.providerName(), now)) {
                throw new IllegalStateException("Provider rate limit exceeded for " + quoteProvider.providerName());
            }
            StockQuote refreshedQuote = quoteProvider.fetchQuote(market, symbol);
            quoteRepository.save(refreshedQuote, stockQuoteProperties.getCacheTtl());
            return refreshedQuote;
        } finally {
            lockHandle.ifPresent(quoteLockService::release);
        }
    }
}
