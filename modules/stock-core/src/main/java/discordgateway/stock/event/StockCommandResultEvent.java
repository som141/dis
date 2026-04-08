package discordgateway.stock.event;

public record StockCommandResultEvent(
        String commandId,
        int schemaVersion,
        long occurredAtEpochMs,
        String producer,
        String targetNode,
        long guildId,
        long requesterId,
        boolean success,
        String message,
        String resultType
) {
}
