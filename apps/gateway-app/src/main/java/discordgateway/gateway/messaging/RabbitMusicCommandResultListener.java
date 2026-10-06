package discordgateway.gateway.messaging;

import discordgateway.common.command.MusicCommandResultEvent;
import discordgateway.gateway.interaction.InteractionResponseContext;
import discordgateway.gateway.interaction.InteractionResponseEditor;
import discordgateway.gateway.interaction.PendingInteractionRepository;
import discordgateway.gateway.presentation.discord.DiscordCommandCatalog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;

public class RabbitMusicCommandResultListener {

    private static final Logger log = LoggerFactory.getLogger(RabbitMusicCommandResultListener.class);

    private final PendingInteractionRepository pendingInteractionRepository;
    private final InteractionResponseEditor interactionResponseEditor;

    public RabbitMusicCommandResultListener(
            PendingInteractionRepository pendingInteractionRepository,
            InteractionResponseEditor interactionResponseEditor
    ) {
        this.pendingInteractionRepository = pendingInteractionRepository;
        this.interactionResponseEditor = interactionResponseEditor;
    }

    @RabbitListener(queues = "#{gatewayCommandResultQueue.name}")
    public synchronized void handle(MusicCommandResultEvent event) {
        // Keep the original playback interaction available for a later decoder/stream failure.
        // A terminal failure consumes it, so an accepted result delivered afterwards cannot overwrite the error.
        boolean playbackFailure = "PLAYBACK_FAILED".equals(event.resultType());
        InteractionResponseContext context = playbackFailure
                ? pendingInteractionRepository.take(event.commandId())
                : pendingInteractionRepository.find(event.commandId());
        if (context != null && !playbackFailure && !retainForPlaybackFailure(context, event)) {
            context = pendingInteractionRepository.take(event.commandId());
        }
        if (context == null) {
            log.atWarn()
                    .addKeyValue("commandId", event.commandId())
                    .addKeyValue("guildId", event.guildId())
                    .addKeyValue("resultType", event.resultType())
                    .log("music-command result dropped because pending interaction was not found");
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

    private boolean retainForPlaybackFailure(InteractionResponseContext context, MusicCommandResultEvent event) {
        return event.success() && (DiscordCommandCatalog.CMD_PLAY.equals(context.commandName())
                || DiscordCommandCatalog.CMD_SFX.equals(context.commandName()));
    }
}
