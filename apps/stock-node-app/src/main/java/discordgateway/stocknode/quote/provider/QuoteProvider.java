package discordgateway.stocknode.quote.provider;

import discordgateway.stocknode.quote.model.StockQuote;

public interface QuoteProvider {

    String providerName();

    StockQuote fetchQuote(String market, String symbol);
}
