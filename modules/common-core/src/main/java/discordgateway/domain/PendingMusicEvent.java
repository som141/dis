package discordgateway.domain;

import discordgateway.application.event.MusicEvent;

public record PendingMusicEvent(
        String eventId,
        MusicEvent event,
        int attemptCount,
        long nextAttemptAtEpochMs,
        String lastError,
        String claimOwner,
        String claimToken,
        long claimUntilEpochMs
) {
}
