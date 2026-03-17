package discordgateway.infrastructure.messaging.rabbit;

import discordgateway.application.event.MusicEvent;
import discordgateway.application.event.MusicEventPublisher;
import discordgateway.bootstrap.MessagingProperties;
import discordgateway.domain.MusicEventOutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RabbitMusicEventPublisher implements MusicEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(RabbitMusicEventPublisher.class);

    private final RabbitMusicEventSender eventSender;
    private final MusicEventOutboxRepository outboxRepository;
    private final MessagingProperties messagingProperties;

    public RabbitMusicEventPublisher(
            RabbitMusicEventSender eventSender,
            MusicEventOutboxRepository outboxRepository,
            MessagingProperties messagingProperties
    ) {
        this.eventSender = eventSender;
        this.outboxRepository = outboxRepository;
        this.messagingProperties = messagingProperties;
    }

    @Override
    public void publish(MusicEvent event) {
        try {
            eventSender.send(event);
        } catch (RuntimeException e) {
            persistForRetry(event, e);
        }
    }

    private void persistForRetry(MusicEvent event, RuntimeException cause) {
        long nextAttemptAt = System.currentTimeMillis() + messagingProperties.getEventOutboxRetryDelayMs();
        try {
            outboxRepository.saveNew(event, nextAttemptAt, summarize(cause));
            log.warn(
                    "Failed to publish rabbit music event. queued for retry. eventId={} type={} guild={} nextAttemptAt={}",
                    event.eventId(),
                    event.eventType(),
                    event.guildId(),
                    nextAttemptAt,
                    cause
            );
        } catch (RuntimeException storageFailure) {
            log.error(
                    "Failed to publish rabbit music event and persist to outbox. eventId={} type={} guild={}",
                    event.eventId(),
                    event.eventType(),
                    event.guildId(),
                    storageFailure
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
