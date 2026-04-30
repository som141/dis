package discordgateway.stocknode.application;

import discordgateway.stocknode.bootstrap.StockQuoteProperties;
import discordgateway.stocknode.persistence.entity.StockAccountEntity;
import discordgateway.stocknode.persistence.entity.StockPositionEntity;
import discordgateway.stocknode.persistence.repository.StockPositionRepository;
import discordgateway.stocknode.quote.service.QuoteService;
import discordgateway.stocknode.quote.service.QuoteUsage;
import discordgateway.stocknode.quote.service.StockQuoteResult;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

public class PortfolioService {

    private final StockPositionRepository stockPositionRepository;
    private final QuoteService quoteService;
    private final StockQuoteProperties stockQuoteProperties;

    public PortfolioService(
            StockPositionRepository stockPositionRepository,
            QuoteService quoteService,
            StockQuoteProperties stockQuoteProperties
    ) {
        this.stockPositionRepository = stockPositionRepository;
        this.quoteService = quoteService;
        this.stockQuoteProperties = stockQuoteProperties;
    }

    public PortfolioView build(StockAccountEntity account, QuoteUsage quoteUsage) {
        List<StockPositionEntity> positions = stockPositionRepository.findAllByAccountIdOrderBySymbolAsc(account.getId());
        List<PortfolioPositionView> entries = new ArrayList<>();
        BigDecimal totalMarketValue = zeroCash();
        BigDecimal totalCostBasis = zeroCash();

        for (StockPositionEntity position : positions) {
            StockQuoteResult quoteResult = quoteService.getQuote(
                    stockQuoteProperties.getDefaultMarket(),
                    position.getSymbol(),
                    quoteUsage
            );
            BigDecimal marketValue = scaleCash(position.getQuantity().multiply(quoteResult.quote().price()));
            BigDecimal costBasis = scaleCash(position.getQuantity().multiply(position.getAverageCost()));
            BigDecimal profitLoss = scaleCash(marketValue.subtract(costBasis));
            entries.add(new PortfolioPositionView(
                    position.getSymbol(),
                    position.getQuantity(),
                    position.getAverageCost(),
                    quoteResult.quote().price(),
                    marketValue,
                    costBasis,
                    profitLoss,
                    quoteResult.fresh()
            ));
            totalMarketValue = totalMarketValue.add(marketValue);
            totalCostBasis = totalCostBasis.add(costBasis);
        }

        BigDecimal totalEquity = scaleCash(account.getCashBalance().add(totalMarketValue));
        BigDecimal totalProfitLoss = scaleCash(totalMarketValue.subtract(totalCostBasis));

        return new PortfolioView(
                account.getId(),
                account.getGuildId(),
                account.getUserId(),
                account.getCashBalance(),
                totalMarketValue,
                totalCostBasis,
                totalEquity,
                totalProfitLoss,
                List.copyOf(entries)
        );
    }

    private BigDecimal zeroCash() {
        return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal scaleCash(BigDecimal value) {
        return value.setScale(4, RoundingMode.HALF_UP);
    }
}
