package discordgateway.application.event;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "eventKind")
@JsonSubTypes({
        @JsonSubTypes.Type(value = MusicEvent.VoiceConnectionChanged.class, name = "voiceConnectionChanged"),
        @JsonSubTypes.Type(value = MusicEvent.AutoPlaySettingChanged.class, name = "autoPlaySettingChanged"),
        @JsonSubTypes.Type(value = MusicEvent.TrackQueued.class, name = "trackQueued"),
        @JsonSubTypes.Type(value = MusicEvent.TrackPlaybackChanged.class, name = "trackPlaybackChanged"),
        @JsonSubTypes.Type(value = MusicEvent.QueueCleared.class, name = "queueCleared"),
        @JsonSubTypes.Type(value = MusicEvent.TrackLoadFailed.class, name = "trackLoadFailed")
})
public sealed interface MusicEvent permits
        MusicEvent.VoiceConnectionChanged,
        MusicEvent.AutoPlaySettingChanged,
        MusicEvent.TrackQueued,
        MusicEvent.TrackPlaybackChanged,
        MusicEvent.QueueCleared,
        MusicEvent.TrackLoadFailed {

    String eventId();

    int schemaVersion();

    long occurredAtEpochMs();

    String producer();

    long guildId();

    String correlationId();

    String eventType();

    record VoiceConnectionChanged(
            String eventId,
            int schemaVersion,
            long occurredAtEpochMs,
            String producer,
            long guildId,
            String correlationId,
            VoiceAction action,
            Long voiceChannelId,
            Long userId,
            String reason
    ) implements MusicEvent {
        @Override
        public String eventType() {
            return "voice.connection.changed";
        }
    }

    record AutoPlaySettingChanged(
            String eventId,
            int schemaVersion,
            long occurredAtEpochMs,
            String producer,
            long guildId,
            String correlationId,
            boolean enabled
    ) implements MusicEvent {
        @Override
        public String eventType() {
            return "playback.autoplay.changed";
        }
    }

    record TrackQueued(
            String eventId,
            int schemaVersion,
            long occurredAtEpochMs,
            String producer,
            long guildId,
            String correlationId,
            String identifier,
            String title,
            String author,
            TransitionSource source
    ) implements MusicEvent {
        @Override
        public String eventType() {
            return "track.queued";
        }
    }

    record TrackPlaybackChanged(
            String eventId,
            int schemaVersion,
            long occurredAtEpochMs,
            String producer,
            long guildId,
            String correlationId,
            PlaybackState state,
            String identifier,
            String title,
            String author,
            TransitionSource source,
            String detail
    ) implements MusicEvent {
        @Override
        public String eventType() {
            return "track.playback.changed";
        }
    }

    record QueueCleared(
            String eventId,
            int schemaVersion,
            long occurredAtEpochMs,
            String producer,
            long guildId,
            String correlationId,
            boolean hadEntries,
            boolean currentTrackPreserved
    ) implements MusicEvent {
        @Override
        public String eventType() {
            return "queue.cleared";
        }
    }

    record TrackLoadFailed(
            String eventId,
            int schemaVersion,
            long occurredAtEpochMs,
            String producer,
            long guildId,
            String correlationId,
            String identifier,
            TransitionSource source,
            String failureType,
            String message
    ) implements MusicEvent {
        @Override
        public String eventType() {
            return "track.load.failed";
        }
    }

    enum VoiceAction {
        CONNECTED,
        DISCONNECTED
    }

    enum PlaybackState {
        STARTED,
        FINISHED,
        STOPPED,
        PAUSED,
        RESUMED
    }

    enum TransitionSource {
        COMMAND,
        QUEUE,
        AUTOPLAY,
        RECOVERY,
        SYSTEM
    }
}
