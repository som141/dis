package discordgateway.application.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;

public class SpringMusicEventPublisher implements MusicEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(SpringMusicEventPublisher.class);

    private final ApplicationEventPublisher delegate;

    public SpringMusicEventPublisher(ApplicationEventPublisher delegate) {
        this.delegate = delegate;
    }

    @Override
    public void publish(MusicEvent event) {
        try {
            delegate.publishEvent(event);
        } catch (RuntimeException e) {
            log.warn("Failed to publish music event. type={} guild={}", event.eventType(), event.guildId(), e);
        }
    }
}
