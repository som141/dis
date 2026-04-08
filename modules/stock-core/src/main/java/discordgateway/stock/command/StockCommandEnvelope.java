package discordgateway.stock.command;

public record StockCommandEnvelope(
        String commandId,
        int schemaVersion,
        long sentAtEpochMs,
        String producer,
        StockCommand command,
        String responseTargetNode
) {
}
