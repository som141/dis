package discordgateway.application;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "commandType")
@JsonSubTypes({
        @JsonSubTypes.Type(value = MusicCommand.Join.class, name = "join"),
        @JsonSubTypes.Type(value = MusicCommand.Leave.class, name = "leave"),
        @JsonSubTypes.Type(value = MusicCommand.Play.class, name = "play"),
        @JsonSubTypes.Type(value = MusicCommand.Stop.class, name = "stop"),
        @JsonSubTypes.Type(value = MusicCommand.Skip.class, name = "skip"),
        @JsonSubTypes.Type(value = MusicCommand.Queue.class, name = "queue"),
        @JsonSubTypes.Type(value = MusicCommand.Clear.class, name = "clear"),
        @JsonSubTypes.Type(value = MusicCommand.Pause.class, name = "pause"),
        @JsonSubTypes.Type(value = MusicCommand.Resume.class, name = "resume"),
        @JsonSubTypes.Type(value = MusicCommand.PlaySfx.class, name = "playSfx")
})
public sealed interface MusicCommand permits
        MusicCommand.Join,
        MusicCommand.Leave,
        MusicCommand.Play,
        MusicCommand.Stop,
        MusicCommand.Skip,
        MusicCommand.Queue,
        MusicCommand.Clear,
        MusicCommand.Pause,
        MusicCommand.Resume,
        MusicCommand.PlaySfx {

    long guildId();

    record Join(
            long guildId,
            long textChannelId,
            long userId
    ) implements MusicCommand {
    }

    record Leave(long guildId) implements MusicCommand {
    }

    record Play(
            long guildId,
            long textChannelId,
            long userId,
            String query,
            boolean autoPlay
    ) implements MusicCommand {
    }

    record Stop(long guildId) implements MusicCommand {
    }

    record Skip(long guildId) implements MusicCommand {
    }

    record Queue(long guildId) implements MusicCommand {
    }

    record Clear(long guildId) implements MusicCommand {
    }

    record Pause(long guildId) implements MusicCommand {
    }

    record Resume(long guildId) implements MusicCommand {
    }

    record PlaySfx(
            long guildId,
            long textChannelId,
            long userId,
            String fileName
    ) implements MusicCommand {
    }
}
