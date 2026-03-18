package discordgateway.domain;

import discordgateway.application.CommandResult;

public interface ProcessedCommandRepository {
    ProcessedCommand find(String commandId);
    boolean tryStart(String commandId, long ttlMillis);
    void complete(String commandId, CommandResult result, long ttlMillis);
    void remove(String commandId);
}
