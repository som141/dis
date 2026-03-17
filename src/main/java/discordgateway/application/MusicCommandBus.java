package discordgateway.application;

import java.util.concurrent.CompletableFuture;

public interface MusicCommandBus {
    CompletableFuture<CommandResult> dispatch(MusicCommand command);
}
