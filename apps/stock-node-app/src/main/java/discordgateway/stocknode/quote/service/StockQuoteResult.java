package discordgateway.stocknode.quote.service;

import discordgateway.stocknode.quote.model.StockQuote;

public record StockQuoteResult(
        StockQuote quote,
        QuoteSource source,
        boolean fresh
) {
}
