package discordgateway.gateway.interaction;

public interface PendingInteractionRepository {
    void put(String commandId, InteractionResponseContext context);
    InteractionResponseContext take(String commandId);
    void remove(String commandId);
}
