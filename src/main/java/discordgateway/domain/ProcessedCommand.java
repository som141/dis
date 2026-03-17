package discordgateway.domain;

import discordgateway.application.CommandResult;

public record ProcessedCommand(
        String commandId,
        CommandProcessingStatus status,
        CommandResult result,
        long updatedAtEpochMs
) {
}
