package discordgateway.stocknode.quote.provider;

import discordgateway.stocknode.quote.model.StockQuote;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FallbackQuoteProvider implements QuoteProvider {

    private static final Logger log = LoggerFactory.getLogger(FallbackQuoteProvider.class);

    private final QuoteProvider primary;
    private final QuoteProvider fallback;
    private final boolean fallbackEnabled;

    public FallbackQuoteProvider(
            QuoteProvider primary,
            QuoteProvider fallback,
            boolean fallbackEnabled
    ) {
        this.primary = primary;
        this.fallback = fallback;
        this.fallbackEnabled = fallbackEnabled;
    }

    @Override
    public String providerName() {
        return primary.providerName();
    }

    @Override
    public StockQuote fetchQuote(String market, String symbol) {
        try {
            return primary.fetchQuote(market, symbol);
        } catch (RuntimeException exception) {
            if (!fallbackEnabled) {
                throw exception;
            }
            log.atWarn()
                    .addKeyValue("market", market)
                    .addKeyValue("symbol", symbol)
                    .addKeyValue("primaryProvider", primary.providerName())
                    .addKeyValue("fallbackProvider", fallback.providerName())
                    .setCause(exception)
                    .log("primary quote provider failed; falling back");
            return fallback.fetchQuote(market, symbol);
        }
    }
}
