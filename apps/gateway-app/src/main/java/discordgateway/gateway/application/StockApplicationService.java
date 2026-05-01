package discordgateway.gateway.application;

import discordgateway.stock.command.StockCommand;
import discordgateway.stock.command.StockCommandEnvelope;
import discordgateway.stock.messaging.StockCommandBus;
import discordgateway.stock.messaging.StockCommandMessageFactory;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class StockApplicationService {

    private static final int MAX_QUOTE_SYMBOLS = 10;

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
                parseSymbols(symbol)
        ));
    }

    public StockCommandEnvelope prepareBuy(
            long guildId,
            long requesterId,
            String symbol,
            BigDecimal quantity,
            Integer leverage
    ) {
        return stockCommandMessageFactory.createEnvelope(new StockCommand.Buy(
                guildId,
                requesterId,
                normalizeSymbol(symbol),
                quantity,
                leverage == null ? 1 : leverage
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

    public StockCommandEnvelope prepareList(long guildId, long requesterId) {
        return stockCommandMessageFactory.createEnvelope(new StockCommand.ListQuotes(guildId, requesterId));
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

    private List<String> parseSymbols(String rawSymbols) {
        List<String> symbols = Arrays.stream((rawSymbols == null ? "" : rawSymbols).split("[,\\s]+"))
                .map(this::normalizeSymbol)
                .filter(symbol -> !symbol.isBlank())
                .distinct()
                .collect(Collectors.toList());

        if (symbols.isEmpty()) {
            throw new IllegalArgumentException("At least one quote symbol is required");
        }
        if (symbols.size() > MAX_QUOTE_SYMBOLS) {
            throw new IllegalArgumentException("At most " + MAX_QUOTE_SYMBOLS + " quote symbols are allowed");
        }
        return symbols;
    }
}
