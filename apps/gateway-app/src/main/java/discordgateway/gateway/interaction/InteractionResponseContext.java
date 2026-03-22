package discordgateway.gateway.interaction;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.interactions.InteractionHook;

public record InteractionResponseContext(
        String interactionToken,
        String commandName,
        long guildId,
        long channelId,
        long createdAtEpochMs,
        long expiresAtEpochMs
) {

    public boolean isExpired(long nowEpochMs) {
        return nowEpochMs >= expiresAtEpochMs;
    }

    public InteractionHook toHook(JDA jda) {
        return InteractionHook.from(jda, interactionToken);
    }
}
