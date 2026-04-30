package discordgateway.gateway.messaging;

import discordgateway.gateway.interaction.InteractionResponseContext;
import discordgateway.gateway.interaction.InteractionResponseEditor;
import discordgateway.gateway.interaction.PendingInteractionRepository;
import discordgateway.stock.event.StockCommandResultEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;

public class RabbitStockCommandResultListener {

    private static final Logger log = LoggerFactory.getLogger(RabbitStockCommandResultListener.class);

    private final PendingInteractionRepository pendingInteractionRepository;
    private final InteractionResponseEditor interactionResponseEditor;

    public RabbitStockCommandResultListener(
            PendingInteractionRepository pendingInteractionRepository,
            InteractionResponseEditor interactionResponseEditor
    ) {
        this.pendingInteractionRepository = pendingInteractionRepository;
        this.interactionResponseEditor = interactionResponseEditor;
    }

    @RabbitListener(queues = "#{gatewayStockCommandResultQueue.name}")
    public void handle(StockCommandResultEvent event) {
        InteractionResponseContext context = pendingInteractionRepository.take(event.commandId());
        if (context == null) {
            log.atWarn()
                    .addKeyValue("commandId", event.commandId())
                    .addKeyValue("guildId", event.guildId())
                    .addKeyValue("resultType", event.resultType())
                    .log("stock-command result dropped because pending interaction was not found");
            return;
        }

        interactionResponseEditor.editOriginal(
                context,
                event.commandId(),
                event.guildId(),
                event.resultType(),
                event.message()
        );
    }
}
