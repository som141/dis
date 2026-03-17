package discordgateway.domain;

import discordgateway.application.event.MusicEvent;

import java.util.List;

public interface MusicEventOutboxRepository {
    void saveNew(MusicEvent event, long nextAttemptAtEpochMs, String lastError);
    List<PendingMusicEvent> claimDue(int limit, long nowEpochMs, String claimerId, long claimTtlMs);
    boolean markSucceeded(String eventId, String claimToken);
    boolean reschedule(String eventId, String claimToken, int nextAttemptCount, long nextAttemptAtEpochMs, String lastError);
}
