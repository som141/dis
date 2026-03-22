package discordgateway.infra.messaging.rabbit;

import discordgateway.common.bootstrap.MessagingProperties;
import discordgateway.common.command.MusicCommandResultEvent;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

public class RabbitMusicCommandResultPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final MessagingProperties messagingProperties;

    public RabbitMusicCommandResultPublisher(
            RabbitTemplate rabbitTemplate,
            MessagingProperties messagingProperties
    ) {
        this.rabbitTemplate = rabbitTemplate;
        this.messagingProperties = messagingProperties;
    }

    public void publish(MusicCommandResultEvent event) {
        rabbitTemplate.convertAndSend(
                messagingProperties.getCommandResultExchange(),
                messagingProperties.commandResultRoutingKey(event.targetNode()),
                event
        );
    }
}
