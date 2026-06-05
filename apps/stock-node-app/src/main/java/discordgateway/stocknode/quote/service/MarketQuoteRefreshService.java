package discordgateway.stocknode.quote.service;

import discordgateway.stocknode.bootstrap.StockQuoteProperties;
import discordgateway.stocknode.cache.QuoteRepository;
import discordgateway.stocknode.lock.QuoteLockHandle;
import discordgateway.stocknode.lock.QuoteLockService;
import discordgateway.stocknode.observability.StockMetricsRecorder;
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
    private final StockMetricsRecorder stockMetricsRecorder;
    private final Clock clock;

    public MarketQuoteRefreshService(
            QuoteRepository quoteRepository,
            QuoteLockService quoteLockService,
            QuoteProvider quoteProvider,
            ProviderRateLimiter providerRateLimiter,
            StockQuoteProperties stockQuoteProperties,
            StockMetricsRecorder stockMetricsRecorder,
            Clock clock
    ) {
        this.quoteRepository = quoteRepository;
        this.quoteLockService = quoteLockService;
        this.quoteProvider = quoteProvider;
        this.providerRateLimiter = providerRateLimiter;
        this.stockQuoteProperties = stockQuoteProperties;
        this.stockMetricsRecorder = stockMetricsRecorder;
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
                stockMetricsRecorder.recordProviderRateLimitExceeded(quoteProvider.providerName());
                stockMetricsRecorder.recordQuoteRefreshFailure(
                        quoteProvider.providerName(),
                        market,
                        symbol,
                        "rate_limit"
                );
                throw new IllegalStateException("Provider rate limit exceeded for " + quoteProvider.providerName());
            }
            StockQuote refreshedQuote = quoteProvider.fetchQuote(market, symbol);
            quoteRepository.save(refreshedQuote, stockQuoteProperties.getCacheTtl());
            stockMetricsRecorder.recordQuoteRefreshSuccess(
                    quoteProvider.providerName(),
                    refreshedQuote.market(),
                    refreshedQuote.symbol()
            );
            return refreshedQuote;
        } catch (RuntimeException exception) {
            String message = exception.getMessage();
            if (message == null || !message.startsWith("Provider rate limit exceeded")) {
                stockMetricsRecorder.recordQuoteRefreshFailure(
                        quoteProvider.providerName(),
                        market,
                        symbol,
                        exception.getClass().getSimpleName()
                );
            }
            throw exception;
        } finally {
            lockHandle.ifPresent(quoteLockService::release);
        }
    }
}
