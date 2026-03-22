package discordgateway.application;

import discordgateway.application.event.MusicEventFactory;
import discordgateway.application.event.MusicEventPublisher;
import discordgateway.domain.GuildStateRepository;
import discordgateway.domain.PlayerStateRepository;
import discordgateway.domain.QueueRepository;
import discordgateway.infrastructure.audio.PlaybackGateway;
import discordgateway.infrastructure.audio.VoiceGateway;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VoiceSessionLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(VoiceSessionLifecycleService.class);

    private final PlaybackGateway playbackGateway;
    private final QueueRepository queueRepository;
    private final VoiceGateway voiceGateway;
    private final GuildStateRepository guildStateRepository;
    private final PlayerStateRepository playerStateRepository;
    private final MusicEventPublisher musicEventPublisher;
    private final MusicEventFactory musicEventFactory;

    public VoiceSessionLifecycleService(
            PlaybackGateway playbackGateway,
            QueueRepository queueRepository,
            VoiceGateway voiceGateway,
            GuildStateRepository guildStateRepository,
            PlayerStateRepository playerStateRepository,
            MusicEventPublisher musicEventPublisher,
            MusicEventFactory musicEventFactory
    ) {
        this.playbackGateway = playbackGateway;
        this.queueRepository = queueRepository;
        this.voiceGateway = voiceGateway;
        this.guildStateRepository = guildStateRepository;
        this.playerStateRepository = playerStateRepository;
        this.musicEventPublisher = musicEventPublisher;
        this.musicEventFactory = musicEventFactory;
    }

    public void terminate(Guild guild, Long userId, String reason) {
        AudioChannel connectedChannel = voiceGateway.connectedChannel(guild);
        Long voiceChannelId = connectedChannel != null ? connectedChannel.getIdLong() : null;
        long guildId = guild.getIdLong();

        log.atInfo()
                .addKeyValue("guildId", guildId)
                .addKeyValue("voiceChannelId", voiceChannelId)
                .addKeyValue("userId", userId)
                .addKeyValue("reason", reason)
                .log("voice-session terminate");

        playbackGateway.stop(guild);
        queueRepository.clear(guildId);
        voiceGateway.disconnect(guild);
        guildStateRepository.remove(guildId);
        playerStateRepository.remove(guildId);

        if (voiceChannelId != null) {
            musicEventPublisher.publish(
                    musicEventFactory.voiceDisconnected(
                            guildId,
                            voiceChannelId,
                            userId,
                            reason
                    )
            );
        }
    }
}
