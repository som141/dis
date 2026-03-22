package discordgateway.playback.domain;

import discordgateway.common.command.CommandResult;

public record ProcessedCommand(
        String commandId,
        CommandProcessingStatus status,
        CommandResult result,
        long updatedAtEpochMs
) {
}
