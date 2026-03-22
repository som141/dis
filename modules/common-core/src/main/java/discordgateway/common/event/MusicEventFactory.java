package discordgateway.common.event;

import discordgateway.common.command.MusicCommandTrace;
import discordgateway.common.command.MusicCommandTraceContext;
import discordgateway.common.command.MusicProtocol;
import discordgateway.common.bootstrap.AppProperties;

import java.util.UUID;

public class MusicEventFactory {

    private final String producer;

    public MusicEventFactory(AppProperties appProperties) {
        String configured = appProperties.getNodeName();
        if (configured == null || configured.isBlank()) {
            this.producer = "discord-gateway";
            return;
        }
        this.producer = configured.trim();
    }

    public MusicEvent.VoiceConnectionChanged voiceConnected(
            long guildId,
            long voiceChannelId,
            Long userId,
            String reason
    ) {
        MusicCommandTrace trace = currentTrace();
        return new MusicEvent.VoiceConnectionChanged(
                newEventId(),
                schemaVersion(trace),
                now(),
                producer,
                guildId,
                correlationId(trace),
                MusicEvent.VoiceAction.CONNECTED,
                voiceChannelId,
                userId,
                reason
        );
    }

    public MusicEvent.VoiceConnectionChanged voiceDisconnected(
            long guildId,
            Long voiceChannelId,
            Long userId,
            String reason
    ) {
        MusicCommandTrace trace = currentTrace();
        return new MusicEvent.VoiceConnectionChanged(
                newEventId(),
                schemaVersion(trace),
                now(),
                producer,
                guildId,
                correlationId(trace),
                MusicEvent.VoiceAction.DISCONNECTED,
                voiceChannelId,
                userId,
                reason
        );
    }

    public MusicEvent.AutoPlaySettingChanged autoPlayChanged(long guildId, boolean enabled) {
        MusicCommandTrace trace = currentTrace();
        return new MusicEvent.AutoPlaySettingChanged(
                newEventId(),
                schemaVersion(trace),
                now(),
                producer,
                guildId,
                correlationId(trace),
                enabled
        );
    }

    public MusicEvent.TrackQueued trackQueued(
            long guildId,
            String identifier,
            String title,
            String author,
            MusicEvent.TransitionSource source
    ) {
        MusicCommandTrace trace = currentTrace();
        return new MusicEvent.TrackQueued(
                newEventId(),
                schemaVersion(trace),
                now(),
                producer,
                guildId,
                correlationId(trace),
                identifier,
                title,
                author,
                source
        );
    }

    public MusicEvent.TrackPlaybackChanged trackPlaybackChanged(
            long guildId,
            MusicEvent.PlaybackState state,
            String identifier,
            String title,
            String author,
            MusicEvent.TransitionSource source,
            String detail
    ) {
        MusicCommandTrace trace = currentTrace();
        return new MusicEvent.TrackPlaybackChanged(
                newEventId(),
                schemaVersion(trace),
                now(),
                producer,
                guildId,
                correlationId(trace),
                state,
                identifier,
                title,
                author,
                source,
                detail
        );
    }

    public MusicEvent.QueueCleared queueCleared(
            long guildId,
            boolean hadEntries,
            boolean currentTrackPreserved
    ) {
        MusicCommandTrace trace = currentTrace();
        return new MusicEvent.QueueCleared(
                newEventId(),
                schemaVersion(trace),
                now(),
                producer,
                guildId,
                correlationId(trace),
                hadEntries,
                currentTrackPreserved
        );
    }

    public MusicEvent.TrackLoadFailed trackLoadFailed(
            long guildId,
            String identifier,
            MusicEvent.TransitionSource source,
            String failureType,
            String message
    ) {
        MusicCommandTrace trace = currentTrace();
        return new MusicEvent.TrackLoadFailed(
                newEventId(),
                schemaVersion(trace),
                now(),
                producer,
                guildId,
                correlationId(trace),
                identifier,
                source,
                failureType,
                message
        );
    }

    private String newEventId() {
        return UUID.randomUUID().toString();
    }

    private long now() {
        return System.currentTimeMillis();
    }

    private MusicCommandTrace currentTrace() {
        return MusicCommandTraceContext.current();
    }

    private int schemaVersion(MusicCommandTrace trace) {
        if (trace == null) {
            return MusicProtocol.SCHEMA_VERSION;
        }
        return trace.schemaVersion();
    }

    private String correlationId(MusicCommandTrace trace) {
        return trace != null ? trace.commandId() : null;
    }
}
