package discordgateway.stocknode.application;

import discordgateway.stocknode.persistence.entity.StockWatchlistEntity;
import discordgateway.stocknode.persistence.repository.StockWatchlistRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

public class StockWatchlistService {

    private final StockWatchlistRepository stockWatchlistRepository;

    public StockWatchlistService(StockWatchlistRepository stockWatchlistRepository) {
        this.stockWatchlistRepository = stockWatchlistRepository;
    }

    @Transactional(readOnly = true)
    public List<StockWatchlistEntity> getEnabledByMarket(String market) {
        return stockWatchlistRepository.findAllByMarketIgnoreCaseAndEnabledTrueOrderByRankNoAsc(normalizeMarket(market));
    }

    @Transactional(readOnly = true)
    public List<StockWatchlistEntity> getEnabledByMarket(String market, int limit) {
        return stockWatchlistRepository.findAllByMarketIgnoreCaseAndEnabledTrueOrderByRankNoAsc(
                normalizeMarket(market),
                PageRequest.of(0, limit)
        );
    }

    @Transactional(readOnly = true)
    public StockWatchlistEntity findByMarketAndSymbol(String market, String symbol) {
        return stockWatchlistRepository.findByMarketIgnoreCaseAndSymbolIgnoreCaseAndEnabledTrue(
                        normalizeMarket(market),
                        normalizeSymbol(symbol)
                )
                .orElseThrow(() -> new SymbolNotTradableException(
                        "Only the enabled " + normalizeMarket(market) + " Top10 watchlist symbols are supported: " + normalizeSymbol(symbol)
                ));
    }

    @Transactional(readOnly = true)
    public StockWatchlistEntity validateTradable(String market, String symbol) {
        return findByMarketAndSymbol(market, symbol);
    }

    private String normalizeMarket(String market) {
        return market == null ? "" : market.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeSymbol(String symbol) {
        return symbol == null ? "" : symbol.trim().toUpperCase(Locale.ROOT);
    }
}
