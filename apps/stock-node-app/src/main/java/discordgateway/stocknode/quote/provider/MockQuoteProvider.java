package discordgateway.stocknode.quote.provider;

import discordgateway.stocknode.quote.model.StockQuote;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

public class MockQuoteProvider implements QuoteProvider {

    private final Clock clock;
    private final AtomicInteger invocationCount = new AtomicInteger();

    public MockQuoteProvider(Clock clock) {
        this.clock = clock;
    }

    @Override
    public String providerName() {
        return "mock";
    }

    @Override
    public StockQuote fetchQuote(String market, String symbol) {
        invocationCount.incrementAndGet();
        long normalizedHash = Math.floorMod(
                Objects.hash(
                        StockQuote.normalizeMarket(market),
                        StockQuote.normalizeSymbol(symbol)
                ),
                1_000
        );
        BigDecimal price = BigDecimal.valueOf(100 + normalizedHash).setScale(2);
        return new StockQuote(market, symbol, price, clock.instant());
    }

    public int invocationCount() {
        return invocationCount.get();
    }
}
