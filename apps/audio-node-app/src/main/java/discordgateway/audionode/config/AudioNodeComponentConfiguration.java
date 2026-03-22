package discordgateway.audionode.config;

import discordgateway.playback.application.MusicWorkerService;
import discordgateway.audionode.recovery.PlaybackRecoveryService;
import discordgateway.playback.application.VoiceSessionLifecycleService;
import discordgateway.common.command.DiscordReferenceResolver;
import discordgateway.audionode.lifecycle.VoiceChannelIdleDisconnectService;
import discordgateway.audionode.lifecycle.VoiceChannelIdleListener;
import discordgateway.common.bootstrap.MessagingProperties;
import discordgateway.common.bootstrap.OperationsProperties;
import discordgateway.audionode.recovery.PlaybackRecoveryReadyListener;
import discordgateway.playback.domain.GuildStateRepository;
import discordgateway.playback.domain.PlayerStateRepository;
import discordgateway.playback.domain.ProcessedCommandRepository;
import discordgateway.playback.domain.QueueRepository;
import discordgateway.infra.audio.PlaybackGateway;
import discordgateway.infra.audio.VoiceGateway;
import discordgateway.infra.messaging.rabbit.RabbitMusicCommandListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;

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
    @ConditionalOnProperty(prefix = "ops", name = "command-dlq-replay-enabled", havingValue = "false", matchIfMissing = true)
    public PlaybackRecoveryReadyListener playbackRecoveryReadyListener(
            PlaybackRecoveryService playbackRecoveryService
    ) {
        return new PlaybackRecoveryReadyListener(playbackRecoveryService);
    }

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnProperty(prefix = "ops", name = "command-dlq-replay-enabled", havingValue = "false", matchIfMissing = true)
    public ScheduledExecutorService voiceIdleDisconnectScheduler() {
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable, "voice-idle-disconnect");
            thread.setDaemon(true);
            return thread;
        };
        return Executors.newSingleThreadScheduledExecutor(threadFactory);
    }

    @Bean
    @ConditionalOnProperty(prefix = "ops", name = "command-dlq-replay-enabled", havingValue = "false", matchIfMissing = true)
    public VoiceChannelIdleDisconnectService voiceChannelIdleDisconnectService(
            VoiceGateway voiceGateway,
            VoiceSessionLifecycleService voiceSessionLifecycleService,
            DiscordReferenceResolver discordReferenceResolver,
            GuildStateRepository guildStateRepository,
            OperationsProperties operationsProperties,
            ScheduledExecutorService voiceIdleDisconnectScheduler
    ) {
        return new VoiceChannelIdleDisconnectService(
                voiceGateway,
                voiceSessionLifecycleService,
                discordReferenceResolver,
                guildStateRepository,
                operationsProperties,
                voiceIdleDisconnectScheduler
        );
    }

    @Bean
    @ConditionalOnProperty(prefix = "ops", name = "command-dlq-replay-enabled", havingValue = "false", matchIfMissing = true)
    public VoiceChannelIdleListener voiceChannelIdleListener(
            VoiceChannelIdleDisconnectService voiceChannelIdleDisconnectService
    ) {
        return new VoiceChannelIdleListener(voiceChannelIdleDisconnectService);
    }
}
