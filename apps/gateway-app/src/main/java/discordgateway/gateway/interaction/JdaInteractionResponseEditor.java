package discordgateway.gateway.interaction;

import net.dv8tion.jda.api.JDA;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JdaInteractionResponseEditor implements InteractionResponseEditor {

    private static final Logger log = LoggerFactory.getLogger(JdaInteractionResponseEditor.class);

    private final JDA jda;

    public JdaInteractionResponseEditor(JDA jda) {
        this.jda = jda;
    }

    @Override
    public void editOriginal(
            InteractionResponseContext context,
            String commandId,
            long guildId,
            String resultType,
            String message
    ) {
        context.toHook(jda).editOriginal(message).queue(
                null,
                failure -> log.atWarn()
                        .addKeyValue("commandId", commandId)
                        .addKeyValue("guildId", guildId)
                        .addKeyValue("resultType", resultType)
                        .setCause(failure)
                        .log("command result reply edit failed")
        );
    }
}
