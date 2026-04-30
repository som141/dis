package discordgateway.gateway.messaging;

import discordgateway.stock.command.StockCommandEnvelope;
import discordgateway.stock.messaging.StockCommandBus;
import discordgateway.stock.messaging.StockMessagingProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.concurrent.CompletableFuture;

public class RabbitStockCommandBus implements StockCommandBus {

    private final RabbitTemplate rabbitTemplate;
    private final StockMessagingProperties messagingProperties;

    public RabbitStockCommandBus(
            RabbitTemplate rabbitTemplate,
            StockMessagingProperties messagingProperties
    ) {
        this.rabbitTemplate = rabbitTemplate;
        this.messagingProperties = messagingProperties;
    }

    @Override
    public CompletableFuture<Void> dispatch(StockCommandEnvelope envelope) {
        rabbitTemplate.convertAndSend(
                messagingProperties.getCommandExchange(),
                messagingProperties.getCommandRoutingKey(),
                envelope
        );
        return CompletableFuture.completedFuture(null);
    }
}
