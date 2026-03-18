package discordgateway.application.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class MusicEventLogListener {

    private static final Logger log = LoggerFactory.getLogger(MusicEventLogListener.class);

    @EventListener
    public void onMusicEvent(MusicEvent event) {
        log.atInfo()
                .addKeyValue("eventType", event.eventType())
                .addKeyValue("schemaVersion", event.schemaVersion())
                .addKeyValue("correlationId", event.correlationId())
                .addKeyValue("guildId", event.guildId())
                .addKeyValue("producer", event.producer())
                .addKeyValue("summary", summarize(event))
                .log("music-event");
    }

    private String summarize(MusicEvent event) {
        if (event instanceof MusicEvent.VoiceConnectionChanged voice) {
            return "action=" + voice.action()
                    + ", channel=" + voice.voiceChannelId()
                    + ", user=" + voice.userId()
                    + ", reason=" + voice.reason();
        }
        if (event instanceof MusicEvent.AutoPlaySettingChanged autoPlay) {
            return "enabled=" + autoPlay.enabled();
        }
        if (event instanceof MusicEvent.TrackQueued queued) {
            return "identifier=" + queued.identifier()
                    + ", title=" + queued.title()
                    + ", source=" + queued.source();
        }
        if (event instanceof MusicEvent.TrackPlaybackChanged playback) {
            return "state=" + playback.state()
                    + ", identifier=" + playback.identifier()
                    + ", title=" + playback.title()
                    + ", source=" + playback.source()
                    + ", detail=" + playback.detail();
        }
        if (event instanceof MusicEvent.QueueCleared cleared) {
            return "hadEntries=" + cleared.hadEntries()
                    + ", currentTrackPreserved=" + cleared.currentTrackPreserved();
        }
        if (event instanceof MusicEvent.TrackLoadFailed failed) {
            return "identifier=" + failed.identifier()
                    + ", source=" + failed.source()
                    + ", failureType=" + failed.failureType()
                    + ", message=" + failed.message();
        }
        return event.toString();
    }
}
