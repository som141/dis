package discordgateway.gateway.application;

import discordgateway.stock.command.StockCommand;
import discordgateway.stock.command.StockCommandEnvelope;
import discordgateway.stock.messaging.StockCommandBus;
import discordgateway.stock.messaging.StockCommandMessageFactory;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public class StockApplicationService {

    private final StockCommandBus stockCommandBus;
    private final StockCommandMessageFactory stockCommandMessageFactory;

    public StockApplicationService(
            StockCommandBus stockCommandBus,
            StockCommandMessageFactory stockCommandMessageFactory
    ) {
        this.stockCommandBus = stockCommandBus;
        this.stockCommandMessageFactory = stockCommandMessageFactory;
    }

    public StockCommandEnvelope prepareQuote(long guildId, long requesterId, String symbol) {
        return stockCommandMessageFactory.createEnvelope(new StockCommand.Quote(
                guildId,
                requesterId,
                normalizeSymbol(symbol)
        ));
    }

    public StockCommandEnvelope prepareBuy(long guildId, long requesterId, String symbol, BigDecimal amount) {
        return stockCommandMessageFactory.createEnvelope(new StockCommand.Buy(
                guildId,
                requesterId,
                normalizeSymbol(symbol),
                amount
        ));
    }

    public StockCommandEnvelope prepareSell(long guildId, long requesterId, String symbol, BigDecimal quantity) {
        return stockCommandMessageFactory.createEnvelope(new StockCommand.Sell(
                guildId,
                requesterId,
                normalizeSymbol(symbol),
                quantity
        ));
    }

    public StockCommandEnvelope prepareBalance(long guildId, long requesterId) {
        return stockCommandMessageFactory.createEnvelope(new StockCommand.Balance(guildId, requesterId));
    }

    public StockCommandEnvelope preparePortfolio(long guildId, long requesterId) {
        return stockCommandMessageFactory.createEnvelope(new StockCommand.Portfolio(guildId, requesterId));
    }

    public StockCommandEnvelope prepareHistory(long guildId, long requesterId, Integer limit) {
        return stockCommandMessageFactory.createEnvelope(new StockCommand.History(guildId, requesterId, limit));
    }

    public StockCommandEnvelope prepareRank(long guildId, long requesterId, String period) {
        return stockCommandMessageFactory.createEnvelope(new StockCommand.Rank(
                guildId,
                requesterId,
                normalizePeriod(period)
        ));
    }

    public CompletableFuture<Void> dispatch(StockCommandEnvelope envelope) {
        return stockCommandBus.dispatch(envelope);
    }

    private String normalizeSymbol(String symbol) {
        return symbol == null ? "" : symbol.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizePeriod(String period) {
        return period == null ? "" : period.trim().toLowerCase(Locale.ROOT);
    }
}
