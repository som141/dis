package discordgateway.stocknode.quote.provider;

import discordgateway.stocknode.application.SymbolNotTradableException;
import discordgateway.stocknode.application.StockWatchlistService;
import discordgateway.stocknode.bootstrap.FinnhubProperties;
import discordgateway.stocknode.quote.finnhub.FinnhubClient;
import discordgateway.stocknode.quote.finnhub.FinnhubQuoteMapper;
import discordgateway.stocknode.quote.model.StockQuote;

public class FinnhubQuoteProvider implements QuoteProvider {

    private final FinnhubClient finnhubClient;
    private final FinnhubQuoteMapper finnhubQuoteMapper;
    private final StockWatchlistService stockWatchlistService;
    private final FinnhubProperties finnhubProperties;

    public FinnhubQuoteProvider(
            FinnhubClient finnhubClient,
            FinnhubQuoteMapper finnhubQuoteMapper,
            StockWatchlistService stockWatchlistService,
            FinnhubProperties finnhubProperties
    ) {
        this.finnhubClient = finnhubClient;
        this.finnhubQuoteMapper = finnhubQuoteMapper;
        this.stockWatchlistService = stockWatchlistService;
        this.finnhubProperties = finnhubProperties;
    }

    @Override
    public String providerName() {
        return "finnhub";
    }

    @Override
    public StockQuote fetchQuote(String market, String symbol) {
        if (finnhubProperties.getApiKey() == null || finnhubProperties.getApiKey().isBlank()) {
            throw new IllegalStateException("Finnhub API key is missing");
        }
        if (!"US".equalsIgnoreCase(market)) {
            throw new SymbolNotTradableException("Finnhub Top10 refresh supports only US market right now");
        }
        return finnhubQuoteMapper.map(
                stockWatchlistService.findByMarketAndSymbol(market, symbol),
                finnhubClient.fetchQuote(symbol)
        );
    }
}
