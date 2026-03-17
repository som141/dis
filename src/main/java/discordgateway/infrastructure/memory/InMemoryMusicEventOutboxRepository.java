package discordgateway.infrastructure.memory;

import discordgateway.application.event.MusicEvent;
import discordgateway.domain.MusicEventOutboxRepository;
import discordgateway.domain.PendingMusicEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;

public class InMemoryMusicEventOutboxRepository implements MusicEventOutboxRepository {

    private final ConcurrentHashMap<String, PendingMusicEvent> store = new ConcurrentHashMap<>();

    @Override
    public void saveNew(MusicEvent event, long nextAttemptAtEpochMs, String lastError) {
        store.putIfAbsent(
                event.eventId(),
                new PendingMusicEvent(
                        event.eventId(),
                        event,
                        1,
                        nextAttemptAtEpochMs,
                        lastError,
                        null,
                        null,
                        0L
                )
        );
    }

    @Override
    public List<PendingMusicEvent> claimDue(int limit, long nowEpochMs, String claimerId, long claimTtlMs) {
        List<PendingMusicEvent> due = new ArrayList<>();
        for (PendingMusicEvent pending : store.values()) {
            if (pending.nextAttemptAtEpochMs() <= nowEpochMs && pending.claimUntilEpochMs() <= nowEpochMs) {
                due.add(pending);
            }
        }

        due.sort(Comparator.comparingLong(PendingMusicEvent::nextAttemptAtEpochMs));
        int max = Math.min(limit, due.size());
        List<PendingMusicEvent> claimed = new ArrayList<>(max);
        for (int i = 0; i < max; i++) {
            PendingMusicEvent candidate = due.get(i);
            String claimToken = UUID.randomUUID().toString();
            long claimUntil = nowEpochMs + claimTtlMs;
            store.computeIfPresent(candidate.eventId(), (ignored, current) -> {
                if (current.nextAttemptAtEpochMs() > nowEpochMs || current.claimUntilEpochMs() > nowEpochMs) {
                    return current;
                }
                return new PendingMusicEvent(
                        current.eventId(),
                        current.event(),
                        current.attemptCount(),
                        current.nextAttemptAtEpochMs(),
                        current.lastError(),
                        claimerId,
                        claimToken,
                        claimUntil
                );
            });

            PendingMusicEvent claimedEvent = store.get(candidate.eventId());
            if (claimedEvent != null && claimToken.equals(claimedEvent.claimToken())) {
                claimed.add(claimedEvent);
            }
        }
        return claimed;
    }

    @Override
    public boolean markSucceeded(String eventId, String claimToken) {
        boolean[] removed = {false};
        store.computeIfPresent(eventId, (ignored, pending) -> {
            if (!claimToken.equals(pending.claimToken())) {
                return pending;
            }
            removed[0] = true;
            return null;
        });
        return removed[0];
    }

    @Override
    public boolean reschedule(
            String eventId,
            String claimToken,
            int nextAttemptCount,
            long nextAttemptAtEpochMs,
            String lastError
    ) {
        boolean[] updated = {false};
        store.computeIfPresent(eventId, (ignored, pending) -> {
            if (!claimToken.equals(pending.claimToken())) {
                return pending;
            }
            updated[0] = true;
            return new PendingMusicEvent(
                    pending.eventId(),
                    pending.event(),
                    nextAttemptCount,
                    nextAttemptAtEpochMs,
                    lastError,
                    null,
                    null,
                    0L
            );
        });
        return updated[0];
    }
}
