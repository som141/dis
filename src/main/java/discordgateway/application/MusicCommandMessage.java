package discordgateway.application;

public record MusicCommandMessage(
        String commandId,
        int schemaVersion,
        long sentAtEpochMs,
        String producer,
        MusicCommand command
) {
}
