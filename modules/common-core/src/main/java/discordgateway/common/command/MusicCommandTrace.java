package discordgateway.common.command;

public record MusicCommandTrace(
        String commandId,
        int schemaVersion,
        String producer
) {
    public static MusicCommandTrace from(MusicCommandMessage message) {
        return new MusicCommandTrace(
                message.commandId(),
                message.schemaVersion(),
                message.producer()
        );
    }
}
