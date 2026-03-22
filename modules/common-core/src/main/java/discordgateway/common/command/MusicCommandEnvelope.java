package discordgateway.common.command;

public record MusicCommandEnvelope(
        MusicCommandMessage message,
        String responseTargetNode,
        MusicCommandResponseMode responseMode
) {
}
