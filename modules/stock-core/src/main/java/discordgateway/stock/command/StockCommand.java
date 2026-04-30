package discordgateway.stock.command;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.math.BigDecimal;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "commandType")
@JsonSubTypes({
        @JsonSubTypes.Type(value = StockCommand.Quote.class, name = "quote"),
        @JsonSubTypes.Type(value = StockCommand.Buy.class, name = "buy"),
        @JsonSubTypes.Type(value = StockCommand.Sell.class, name = "sell"),
        @JsonSubTypes.Type(value = StockCommand.Balance.class, name = "balance"),
        @JsonSubTypes.Type(value = StockCommand.Portfolio.class, name = "portfolio"),
        @JsonSubTypes.Type(value = StockCommand.History.class, name = "history"),
        @JsonSubTypes.Type(value = StockCommand.Rank.class, name = "rank")
})
public sealed interface StockCommand permits
        StockCommand.Quote,
        StockCommand.Buy,
        StockCommand.Sell,
        StockCommand.Balance,
        StockCommand.Portfolio,
        StockCommand.History,
        StockCommand.Rank {

    long guildId();

    long requesterId();

    record Quote(
            long guildId,
            long requesterId,
            String symbol
    ) implements StockCommand {
    }

    record Buy(
            long guildId,
            long requesterId,
            String symbol,
            BigDecimal amount
    ) implements StockCommand {
    }

    record Sell(
            long guildId,
            long requesterId,
            String symbol,
            BigDecimal quantity
    ) implements StockCommand {
    }

    record Balance(
            long guildId,
            long requesterId
    ) implements StockCommand {
    }

    record Portfolio(
            long guildId,
            long requesterId
    ) implements StockCommand {
    }

    record History(
            long guildId,
            long requesterId,
            Integer limit
    ) implements StockCommand {
    }

    record Rank(
            long guildId,
            long requesterId,
            String period
    ) implements StockCommand {
    }
}
