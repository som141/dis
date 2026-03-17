package discordgateway.infrastructure.messaging.rabbit;

import discordgateway.bootstrap.AppProperties;
import discordgateway.bootstrap.MessagingProperties;
import discordgateway.domain.MusicEventOutboxRepository;
import discordgateway.domain.PendingMusicEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.List;

public class MusicEventOutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(MusicEventOutboxRelay.class);

    private final RabbitMusicEventSender eventSender;
    private final MusicEventOutboxRepository outboxRepository;
    private final MessagingProperties messagingProperties;
    private final String claimerId;

    public MusicEventOutboxRelay(
            RabbitMusicEventSender eventSender,
            MusicEventOutboxRepository outboxRepository,
            MessagingProperties messagingProperties,
            AppProperties appProperties
    ) {
        this.eventSender = eventSender;
        this.outboxRepository = outboxRepository;
        this.messagingProperties = messagingProperties;
        this.claimerId = appProperties.getNodeName();
    }

    @Scheduled(fixedDelayString = "${messaging.event-outbox-flush-interval-ms:5000}")
    public void flush() {
        long now = System.currentTimeMillis();
        List<PendingMusicEvent> dueEvents = outboxRepository.claimDue(
                messagingProperties.getEventOutboxBatchSize(),
                now,
                claimerId,
                messagingProperties.getEventOutboxClaimTtlMs()
        );
        if (dueEvents.isEmpty()) {
            return;
        }

        for (PendingMusicEvent pending : dueEvents) {
            replay(pending);
        }
    }

    private void replay(PendingMusicEvent pending) {
        try {
            eventSender.send(pending.event());
            boolean removed = outboxRepository.markSucceeded(pending.eventId(), pending.claimToken());
            if (!removed) {
                log.warn(
                        "music-event outbox success ignored due to lost claim eventId={} claimer={} claimOwner={} claimUntil={}",
                        pending.eventId(),
                        claimerId,
                        pending.claimOwner(),
                        pending.claimUntilEpochMs()
                );
                return;
            }
            log.info(
                    "music-event outbox replayed eventId={} type={} guild={} attempts={} claimer={}",
                    pending.eventId(),
                    pending.event().eventType(),
                    pending.event().guildId(),
                    pending.attemptCount(),
                    claimerId
            );
        } catch (RuntimeException e) {
            int nextAttemptCount = pending.attemptCount() + 1;
            long nextAttemptAt = System.currentTimeMillis() + messagingProperties.getEventOutboxRetryDelayMs();
            boolean rescheduled = outboxRepository.reschedule(
                    pending.eventId(),
                    pending.claimToken(),
                    nextAttemptCount,
                    nextAttemptAt,
                    summarize(e)
            );
            if (!rescheduled) {
                log.warn(
                        "music-event outbox failure ignored due to lost claim eventId={} claimer={} claimOwner={} claimUntil={}",
                        pending.eventId(),
                        claimerId,
                        pending.claimOwner(),
                        pending.claimUntilEpochMs(),
                        e
                );
                return;
            }
            log.warn(
                    "music-event outbox replay failed eventId={} type={} guild={} attempts={} nextAttemptAt={} claimer={}",
                    pending.eventId(),
                    pending.event().eventType(),
                    pending.event().guildId(),
                    nextAttemptCount,
                    nextAttemptAt,
                    claimerId,
                    e
            );
        }
    }

    private String summarize(Throwable throwable) {
        String message = throwable.getMessage();
        if (message == null || message.isBlank()) {
            return throwable.getClass().getSimpleName();
        }
        return throwable.getClass().getSimpleName() + ": " + message;
    }
}
