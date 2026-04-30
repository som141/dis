package discordgateway.stock.messaging;

import discordgateway.stock.command.StockCommandEnvelope;

import java.util.concurrent.CompletableFuture;

public interface StockCommandBus {

    CompletableFuture<Void> dispatch(StockCommandEnvelope envelope);
}
