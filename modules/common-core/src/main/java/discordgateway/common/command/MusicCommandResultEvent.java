package discordgateway.common.command;

public record MusicCommandResultEvent(
        String commandId,
        int schemaVersion,
        long occurredAtEpochMs,
        String producer,
        String targetNode,
        long guildId,
        boolean success,
        String message,
        boolean ephemeral,
        String resultType
) {
}
