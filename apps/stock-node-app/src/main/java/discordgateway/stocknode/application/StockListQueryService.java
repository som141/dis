package discordgateway.stocknode.application;

import discordgateway.stocknode.bootstrap.StockMarketDataProperties;
import discordgateway.stocknode.persistence.entity.StockWatchlistEntity;
import discordgateway.stocknode.quote.service.QuoteService;
import discordgateway.stocknode.quote.service.QuoteUsage;
import discordgateway.stocknode.quote.service.StockQuoteResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public class StockListQueryService {

    private final StockWatchlistService stockWatchlistService;
    private final QuoteService quoteService;
    private final StockMarketDataProperties stockMarketDataProperties;

    public StockListQueryService(
            StockWatchlistService stockWatchlistService,
            QuoteService quoteService,
            StockMarketDataProperties stockMarketDataProperties
    ) {
        this.stockWatchlistService = stockWatchlistService;
        this.quoteService = quoteService;
        this.stockMarketDataProperties = stockMarketDataProperties;
    }

    @Transactional(readOnly = true)
    public StockListView getUsTopList() {
        List<StockListItemView> items = stockWatchlistService
                .getEnabledByMarket(stockMarketDataProperties.getMarket(), stockMarketDataProperties.getTopRankLimit())
                .stream()
                .map(this::toItem)
                .toList();
        return new StockListView(stockMarketDataProperties.getMarket(), items);
    }

    private StockListItemView toItem(StockWatchlistEntity entity) {
        Optional<StockQuoteResult> quoteResult = quoteService.findCachedQuote(
                entity.getMarket(),
                entity.getSymbol(),
                QuoteUsage.QUERY
        );

        return quoteResult.map(result -> new StockListItemView(
                        entity.getRankNo(),
                        entity.getMarket(),
                        entity.getSymbol(),
                        entity.getName(),
                        result.quote().price(),
                        result.quote().changeRate(),
                        true,
                        result.fresh()
                ))
                .orElseGet(() -> new StockListItemView(
                        entity.getRankNo(),
                        entity.getMarket(),
                        entity.getSymbol(),
                        entity.getName(),
                        null,
                        null,
                        false,
                        false
                ));
    }
}
