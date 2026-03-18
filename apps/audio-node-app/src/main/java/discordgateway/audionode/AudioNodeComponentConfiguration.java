package discordgateway.audionode;

import discordgateway.application.MusicWorkerService;
import discordgateway.application.PlaybackRecoveryService;
import discordgateway.bootstrap.MessagingProperties;
import discordgateway.discord.PlaybackRecoveryReadyListener;
import discordgateway.domain.GuildStateRepository;
import discordgateway.domain.PlayerStateRepository;
import discordgateway.domain.ProcessedCommandRepository;
import discordgateway.domain.QueueRepository;
import discordgateway.infrastructure.audio.PlaybackGateway;
import discordgateway.infrastructure.audio.VoiceGateway;
import discordgateway.infrastructure.messaging.rabbit.RabbitMusicCommandListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class AudioNodeComponentConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "ops", name = "command-dlq-replay-enabled", havingValue = "false", matchIfMissing = true)
    public RabbitMusicCommandListener rabbitMusicCommandListener(
            MusicWorkerService musicWorkerService,
            ProcessedCommandRepository processedCommandRepository,
            MessagingProperties messagingProperties
    ) {
        return new RabbitMusicCommandListener(
                musicWorkerService,
                processedCommandRepository,
                messagingProperties
        );
    }

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
