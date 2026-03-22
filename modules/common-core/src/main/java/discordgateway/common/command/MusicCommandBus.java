package discordgateway.common.command;

import java.util.concurrent.CompletableFuture;

public interface MusicCommandBus {
    CompletableFuture<CommandDispatchAck> dispatch(MusicCommandEnvelope envelope);
}
