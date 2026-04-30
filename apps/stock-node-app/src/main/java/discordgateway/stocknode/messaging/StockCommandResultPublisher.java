package discordgateway.stocknode.messaging;

import discordgateway.stock.event.StockCommandResultEvent;
import discordgateway.stocknode.bootstrap.StockNodeMessagingProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

public class StockCommandResultPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final StockNodeMessagingProperties messagingProperties;

    public StockCommandResultPublisher(
            RabbitTemplate rabbitTemplate,
            StockNodeMessagingProperties messagingProperties
    ) {
        this.rabbitTemplate = rabbitTemplate;
        this.messagingProperties = messagingProperties;
    }

    public void publish(StockCommandResultEvent event) {
        rabbitTemplate.convertAndSend(
                messagingProperties.getCommandResultExchange(),
                messagingProperties.commandResultRoutingKey(event.targetNode()),
                event
        );
    }
}
