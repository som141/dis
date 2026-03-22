package discordgateway.gateway.messaging;

import discordgateway.common.command.MusicCommandResultEvent;
import discordgateway.gateway.interaction.InteractionResponseContext;
import discordgateway.gateway.interaction.PendingInteractionRepository;
import net.dv8tion.jda.api.JDA;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;

public class RabbitMusicCommandResultListener {

    private static final Logger log = LoggerFactory.getLogger(RabbitMusicCommandResultListener.class);

    private final PendingInteractionRepository pendingInteractionRepository;
    private final JDA jda;

    public RabbitMusicCommandResultListener(
            PendingInteractionRepository pendingInteractionRepository,
            JDA jda
    ) {
        this.pendingInteractionRepository = pendingInteractionRepository;
        this.jda = jda;
    }

    @RabbitListener(queues = "#{gatewayCommandResultQueue.name}")
    public void handle(MusicCommandResultEvent event) {
        InteractionResponseContext context = pendingInteractionRepository.take(event.commandId());
        if (context == null) {
            log.atWarn()
                    .addKeyValue("commandId", event.commandId())
                    .addKeyValue("guildId", event.guildId())
                    .addKeyValue("resultType", event.resultType())
                    .log("music-command result dropped because pending interaction was not found");
            return;
        }

        context.toHook(jda).editOriginal(event.message()).queue(
                null,
                failure -> log.atWarn()
                        .addKeyValue("commandId", event.commandId())
                        .addKeyValue("guildId", event.guildId())
                        .addKeyValue("resultType", event.resultType())
                        .setCause(failure)
                        .log("music-command result reply edit failed")
        );
    }
}
