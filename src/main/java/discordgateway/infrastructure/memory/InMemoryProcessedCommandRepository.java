package discordgateway.infrastructure.memory;

import discordgateway.application.CommandResult;
import discordgateway.domain.CommandProcessingStatus;
import discordgateway.domain.ProcessedCommand;
import discordgateway.domain.ProcessedCommandRepository;

import java.util.concurrent.ConcurrentHashMap;

public class InMemoryProcessedCommandRepository implements ProcessedCommandRepository {

    private final ConcurrentHashMap<String, TimedProcessedCommand> store = new ConcurrentHashMap<>();

    @Override
    public ProcessedCommand find(String commandId) {
        TimedProcessedCommand timed = store.get(commandId);
        if (timed == null) {
            return null;
        }
        if (timed.expiresAtEpochMs() <= System.currentTimeMillis()) {
            store.remove(commandId, timed);
            return null;
        }
        return timed.command();
    }

    @Override
    public boolean tryStart(String commandId, long ttlMillis) {
        long now = System.currentTimeMillis();
        long expiresAt = now + ttlMillis;
        final boolean[] acquired = {false};

        store.compute(commandId, (ignored, existing) -> {
            if (existing == null || existing.expiresAtEpochMs() <= now) {
                acquired[0] = true;
                return new TimedProcessedCommand(
                        new ProcessedCommand(
                                commandId,
                                CommandProcessingStatus.PROCESSING,
                                null,
                                now
                        ),
                        expiresAt
                );
            }
            return existing;
        });

        return acquired[0];
    }

    @Override
    public void complete(String commandId, CommandResult result, long ttlMillis) {
        long now = System.currentTimeMillis();
        store.put(
                commandId,
                new TimedProcessedCommand(
                        new ProcessedCommand(
                                commandId,
                                CommandProcessingStatus.COMPLETED,
                                result,
                                now
                        ),
                        now + ttlMillis
                )
        );
    }

    @Override
    public void remove(String commandId) {
        store.remove(commandId);
    }

    private record TimedProcessedCommand(
            ProcessedCommand command,
            long expiresAtEpochMs
    ) {
    }
}
