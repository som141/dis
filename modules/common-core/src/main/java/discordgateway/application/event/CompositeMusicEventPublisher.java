package discordgateway.application.event;

import java.util.List;

public class CompositeMusicEventPublisher implements MusicEventPublisher {

    private final List<MusicEventPublisher> delegates;

    public CompositeMusicEventPublisher(List<MusicEventPublisher> delegates) {
        this.delegates = List.copyOf(delegates);
    }

    @Override
    public void publish(MusicEvent event) {
        for (MusicEventPublisher delegate : delegates) {
            delegate.publish(event);
        }
    }
}
