package discordgateway.gateway.interaction;

public interface InteractionResponseEditor {

    void editOriginal(InteractionResponseContext context, String commandId, long guildId, String resultType, String message);
}
