package discordgateway.stocknode.messaging;

import discordgateway.stock.event.StockCommandResultEvent;
import discordgateway.stocknode.bootstrap.StockNodeMessagingProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StockCommandResultPublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Test
    void publishesToTargetNodeRoutingKey() {
        StockNodeMessagingProperties messagingProperties = new StockNodeMessagingProperties();
        StockCommandResultPublisher publisher = new StockCommandResultPublisher(rabbitTemplate, messagingProperties);
        StockCommandResultEvent event = new StockCommandResultEvent(
                "cmd-1",
                1,
                1_234L,
                "stock-node-1",
                "gateway-1",
                1001L,
                2002L,
                true,
                "ok",
                "BALANCE"
        );

        publisher.publish(event);

        verify(rabbitTemplate).convertAndSend(
                "stock.command.result.exchange",
                "stock.command.result.gateway-1",
                event
        );
    }
}
