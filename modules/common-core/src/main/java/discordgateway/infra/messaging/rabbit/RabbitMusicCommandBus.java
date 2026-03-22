package discordgateway.infra.messaging.rabbit;

import discordgateway.common.bootstrap.MessagingProperties;
import discordgateway.common.command.CommandDispatchAck;
import discordgateway.common.command.MusicCommandBus;
import discordgateway.common.command.MusicCommandEnvelope;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.concurrent.CompletableFuture;

public class RabbitMusicCommandBus implements MusicCommandBus {

    private final RabbitTemplate rabbitTemplate;
    private final MessagingProperties messagingProperties;

    public RabbitMusicCommandBus(
            RabbitTemplate rabbitTemplate,
            MessagingProperties messagingProperties
    ) {
        this.rabbitTemplate = rabbitTemplate;
        this.messagingProperties = messagingProperties;
    }

    @Override
    public CompletableFuture<CommandDispatchAck> dispatch(MusicCommandEnvelope envelope) {
        rabbitTemplate.convertAndSend(
                messagingProperties.getCommandExchange(),
                messagingProperties.getCommandRoutingKey(),
                envelope
        );
        return CompletableFuture.completedFuture(new CommandDispatchAck(envelope.message().commandId()));
    }
}
