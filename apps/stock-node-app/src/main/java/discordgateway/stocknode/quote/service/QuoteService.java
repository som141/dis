package discordgateway.stocknode.quote.service;

import discordgateway.stocknode.bootstrap.StockQuoteProperties;
import discordgateway.stocknode.cache.QuoteRepository;
import discordgateway.stocknode.lock.QuoteLockHandle;
import discordgateway.stocknode.lock.QuoteLockService;
import discordgateway.stocknode.quote.model.StockQuote;
import discordgateway.stocknode.quote.provider.QuoteProvider;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

public class QuoteService {

    private final QuoteRepository quoteRepository;
    private final QuoteLockService quoteLockService;
    private final QuoteProvider quoteProvider;
    private final ProviderRateLimiter providerRateLimiter;
    private final StockQuoteProperties stockQuoteProperties;
    private final Clock clock;

    public QuoteService(
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

    public StockQuoteResult getQuote(String market, String symbol, QuoteUsage quoteUsage) {
        Instant now = clock.instant();
        Optional<StockQuote> cachedQuote = quoteRepository.find(market, symbol);
        if (cachedQuote.isPresent() && isFresh(cachedQuote.get(), quoteUsage, now)) {
            return new StockQuoteResult(cachedQuote.get(), QuoteSource.CACHE_FRESH, true);
        }

        return refreshOrFallback(market, symbol, quoteUsage, cachedQuote, now);
    }

    private StockQuoteResult refreshOrFallback(
            String market,
            String symbol,
            QuoteUsage quoteUsage,
            Optional<StockQuote> cachedQuote,
            Instant now
    ) {
        Optional<QuoteLockHandle> quoteLockHandle = quoteLockService.tryAcquire(market, symbol);
        if (quoteLockHandle.isEmpty()) {
            return waitForRefreshedQuote(market, symbol, quoteUsage, cachedQuote);
        }

        try {
            if (!providerRateLimiter.tryConsume(quoteProvider.providerName(), now)) {
                return cachedQuote
                        .map(this::staleResult)
                        .orElseThrow(() -> new IllegalStateException("Provider rate limit exceeded without cached quote"));
            }

            StockQuote refreshedQuote = quoteProvider.fetchQuote(market, symbol);
            quoteRepository.save(refreshedQuote, stockQuoteProperties.getCacheTtl());

            QuoteSource quoteSource = cachedQuote.isPresent()
                    ? QuoteSource.PROVIDER_REFRESH
                    : QuoteSource.PROVIDER_MISS;
            return new StockQuoteResult(refreshedQuote, quoteSource, true);
        } finally {
            quoteLockHandle.ifPresent(quoteLockService::release);
        }
    }

    private StockQuoteResult waitForRefreshedQuote(
            String market,
            String symbol,
            QuoteUsage quoteUsage,
            Optional<StockQuote> cachedQuote
    ) {
        long deadlineNanos = System.nanoTime() + stockQuoteProperties.getLockWaitTimeout().toNanos();

        // Another worker may already be refreshing the same quote. Poll the cache briefly first.
        while (System.nanoTime() < deadlineNanos) {
            Optional<StockQuote> refreshedQuote = quoteRepository.find(market, symbol);
            if (refreshedQuote.isPresent() && isFresh(refreshedQuote.get(), quoteUsage, clock.instant())) {
                return new StockQuoteResult(refreshedQuote.get(), QuoteSource.CACHE_FRESH, true);
            }
            sleep(stockQuoteProperties.getLockPollInterval());
        }

        return cachedQuote
                .map(this::staleResult)
                .orElseThrow(() -> new IllegalStateException("Quote refresh already in progress and no cached quote is available"));
    }

    private boolean isFresh(StockQuote stockQuote, QuoteUsage quoteUsage, Instant now) {
        Duration maxAge = switch (quoteUsage) {
            case QUERY -> stockQuoteProperties.getQueryFreshness();
            case TRADE -> stockQuoteProperties.getTradeFreshness();
            case RANK -> stockQuoteProperties.getRankFreshness();
        };
        return !stockQuote.quotedAt().isBefore(now.minus(maxAge));
    }

    private StockQuoteResult staleResult(StockQuote stockQuote) {
        return new StockQuoteResult(stockQuote, QuoteSource.CACHE_STALE, false);
    }

    private void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for a quote refresh", exception);
        }
    }
}
