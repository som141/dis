package discordgateway.playback.domain;

import discordgateway.common.command.CommandResult;

public interface ProcessedCommandRepository {
    ProcessedCommand find(String commandId);
    boolean tryStart(String commandId, long ttlMillis);
    void complete(String commandId, CommandResult result, long ttlMillis);
    void remove(String commandId);
}
