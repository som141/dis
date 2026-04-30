package discordgateway.stock.messaging;

import discordgateway.stock.command.StockCommand;
import discordgateway.stock.command.StockCommandEnvelope;

import java.util.UUID;

public class StockCommandMessageFactory {

    private final String producer;

    public StockCommandMessageFactory(String producer) {
        if (producer == null || producer.isBlank()) {
            this.producer = "discord-gateway";
            return;
        }
        this.producer = producer.trim();
    }

    public StockCommandEnvelope createEnvelope(StockCommand command) {
        return new StockCommandEnvelope(
                UUID.randomUUID().toString(),
                StockProtocol.SCHEMA_VERSION,
                System.currentTimeMillis(),
                producer,
                command,
                producer
        );
    }
}
