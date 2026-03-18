package discordgateway.audionode;

import discordgateway.application.PlaybackRecoveryService;
import discordgateway.discord.PlaybackRecoveryReadyListener;
import discordgateway.domain.GuildStateRepository;
import discordgateway.domain.PlayerStateRepository;
import discordgateway.domain.QueueRepository;
import discordgateway.infrastructure.audio.PlaybackGateway;
import discordgateway.infrastructure.audio.VoiceGateway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class AudioNodeComponentConfiguration {

    @Bean
    public PlaybackRecoveryService playbackRecoveryService(
            PlaybackGateway playbackGateway,
            VoiceGateway voiceGateway,
            GuildStateRepository guildStateRepository,
            PlayerStateRepository playerStateRepository,
            QueueRepository queueRepository
    ) {
        return new PlaybackRecoveryService(
                playbackGateway,
                voiceGateway,
                guildStateRepository,
                playerStateRepository,
                queueRepository
        );
    }

    @Bean
    public PlaybackRecoveryReadyListener playbackRecoveryReadyListener(
            PlaybackRecoveryService playbackRecoveryService
    ) {
        return new PlaybackRecoveryReadyListener(playbackRecoveryService);
    }
}
