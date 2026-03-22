package discordgateway.infra.messaging.rabbit;

public record CommandDlqReplayReport(
        int replayedCount,
        int failedCount,
        boolean stoppedByLimit
) {
}
