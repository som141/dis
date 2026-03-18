package discordgateway.application;

import discordgateway.domain.GuildPlayerState;
import discordgateway.domain.GuildStateRepository;
import discordgateway.domain.PlayerState;
import discordgateway.domain.PlayerStateRepository;
import discordgateway.domain.QueueEntry;
import discordgateway.domain.QueueRepository;
import discordgateway.infrastructure.audio.PlaybackGateway;
import discordgateway.infrastructure.audio.VoiceGateway;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;

import java.util.List;

public class PlaybackRecoveryService {

    private final PlaybackGateway playbackGateway;
    private final VoiceGateway voiceGateway;
    private final GuildStateRepository guildStateRepository;
    private final PlayerStateRepository playerStateRepository;
    private final QueueRepository queueRepository;

    public PlaybackRecoveryService(
            PlaybackGateway playbackGateway,
            VoiceGateway voiceGateway,
            GuildStateRepository guildStateRepository,
            PlayerStateRepository playerStateRepository,
            QueueRepository queueRepository
    ) {
        this.playbackGateway = playbackGateway;
        this.voiceGateway = voiceGateway;
        this.guildStateRepository = guildStateRepository;
        this.playerStateRepository = playerStateRepository;
        this.queueRepository = queueRepository;
    }

    public void recoverAll(List<Guild> guilds) {
        for (Guild guild : guilds) {
            recoverGuild(guild);
        }
    }

    private void recoverGuild(Guild guild) {
        long guildId = guild.getIdLong();

        GuildPlayerState guildState = guildStateRepository.getOrCreate(guildId);
        Long connectedVoiceChannelId = guildState.getConnectedVoiceChannelId();
        if (connectedVoiceChannelId == null) {
            return;
        }

        PlayerState playerState = playerStateRepository.getOrCreate(guildId);
        String nowPlaying = blankToNull(playerState.getNowPlaying());
        boolean hasQueuedEntries = queueRepository.hasEntries(guildId);

        if (nowPlaying == null && !hasQueuedEntries) {
            return;
        }

        AudioChannel audioChannel = resolveAudioChannel(guild, connectedVoiceChannelId);
        if (audioChannel == null) {
            return;
        }

        try {
            voiceGateway.connect(guild, audioChannel);
            playbackGateway.setAutoPlay(guild, playerState.isAutoPlay());
        } catch (Exception e) {
            return;
        }

        if (nowPlaying != null) {
            playbackGateway.recover(guild, nowPlaying)
                    .thenAccept(recovered -> {
                        if (!recovered) {
                            recoverNextQueuedEntry(guild);
                        }
                    });
            return;
        }

        recoverNextQueuedEntry(guild);
    }

    private void recoverNextQueuedEntry(Guild guild) {
        long guildId = guild.getIdLong();
        QueueEntry nextEntry = queueRepository.poll(guildId);
        if (nextEntry == null) {
            return;
        }

        playbackGateway.recover(guild, nextEntry.identifier())
                .thenAccept(recovered -> {
                    if (!recovered) {
                        recoverNextQueuedEntry(guild);
                    }
                });
    }

    private AudioChannel resolveAudioChannel(Guild guild, long channelId) {
        try {
            return guild.getChannelById(AudioChannel.class, channelId);
        } catch (Exception e) {
            return null;
        }
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }
}
