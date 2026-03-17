package discordgateway.infrastructure.messaging.rabbit;

import discordgateway.application.CommandResult;
import discordgateway.application.MusicCommand;
import discordgateway.application.MusicCommandBus;
import discordgateway.application.MusicCommandMessageFactory;
import discordgateway.bootstrap.MessagingProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.core.ParameterizedTypeReference;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class RabbitMusicCommandBus implements MusicCommandBus {

    private static final ParameterizedTypeReference<CommandResult> COMMAND_RESULT_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final RabbitTemplate rabbitTemplate;
    private final MessagingProperties messagingProperties;
    private final MusicCommandMessageFactory messageFactory;
    private final Executor executor;

    public RabbitMusicCommandBus(
            RabbitTemplate rabbitTemplate,
            MessagingProperties messagingProperties,
            MusicCommandMessageFactory messageFactory,
            Executor executor
    ) {
        this.rabbitTemplate = rabbitTemplate;
        this.messagingProperties = messagingProperties;
        this.messageFactory = messageFactory;
        this.executor = executor;
        this.rabbitTemplate.setReplyTimeout(messagingProperties.getRpcTimeoutMs());
    }

    @Override
    public CompletableFuture<CommandResult> dispatch(MusicCommand command) {
        return CompletableFuture.supplyAsync(() -> dispatchBlocking(command), executor);
    }

    private CommandResult dispatchBlocking(MusicCommand command) {
        CommandResult result = rabbitTemplate.convertSendAndReceiveAsType(
                messagingProperties.getCommandExchange(),
                messagingProperties.getCommandRoutingKey(),
                messageFactory.create(command),
                COMMAND_RESULT_TYPE
        );

        return Objects.requireNonNull(
                result,
                () -> "Rabbit command RPC returned no response for command " + command.getClass().getSimpleName()
        );
    }
}
