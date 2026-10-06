package discordgateway.common.command;

public record MusicCommandTrace(
        String commandId,
        int schemaVersion,
        String producer,
        String responseTargetNode,
        MusicCommandResponseMode responseMode
) {
    public MusicCommandTrace(String commandId, int schemaVersion, String producer) {
        this(commandId, schemaVersion, producer, null, null);
    }

    public static MusicCommandTrace from(MusicCommandMessage message) {
        return new MusicCommandTrace(
                message.commandId(),
                message.schemaVersion(),
                message.producer()
        );
    }

    public static MusicCommandTrace from(MusicCommandEnvelope envelope) {
        MusicCommandMessage message = envelope.message();
        return new MusicCommandTrace(message.commandId(), message.schemaVersion(), message.producer(),
                envelope.responseTargetNode(), envelope.responseMode());
    }
}
