package discordgateway.bootstrap;

import com.fasterxml.jackson.databind.ObjectMapper;
import discordgateway.application.DiscordReferenceResolver;
import discordgateway.application.InProcessMusicCommandBus;
import discordgateway.application.MusicApplicationService;
import discordgateway.application.MusicCommandBus;
import discordgateway.application.MusicCommandMessageFactory;
import discordgateway.application.MusicWorkerService;
import discordgateway.application.PlayAutocompleteService;
import discordgateway.application.PlaybackRecoveryService;
import discordgateway.application.event.MusicEventFactory;
import discordgateway.application.event.MusicEventPublisher;
import discordgateway.application.event.SpringMusicEventPublisher;
import discordgateway.audio.PlayerManager;
import discordgateway.discord.DiscordBotListener;
import discordgateway.discord.DiscordCommandRegistrationListener;
import discordgateway.discord.PlaybackRecoveryReadyListener;
import discordgateway.domain.GuildPlaybackLockManager;
import discordgateway.domain.GuildStateRepository;
import discordgateway.domain.MusicEventOutboxRepository;
import discordgateway.domain.PlayerStateRepository;
import discordgateway.domain.ProcessedCommandRepository;
import discordgateway.domain.QueueRepository;
import discordgateway.infrastructure.audio.JdaVoiceGateway;
import discordgateway.infrastructure.audio.LavaPlayerPlaybackGateway;
import discordgateway.infrastructure.audio.PlaybackGateway;
import discordgateway.infrastructure.audio.VoiceGateway;
import discordgateway.infrastructure.discord.JdaDiscordReferenceResolver;
import discordgateway.infrastructure.discord.JdaRuntimeContext;
import discordgateway.infrastructure.memory.InMemoryGuildPlaybackLockManager;
import discordgateway.infrastructure.memory.InMemoryGuildStateRepository;
import discordgateway.infrastructure.memory.InMemoryMusicEventOutboxRepository;
import discordgateway.infrastructure.memory.InMemoryPlayerStateRepository;
import discordgateway.infrastructure.memory.InMemoryProcessedCommandRepository;
import discordgateway.infrastructure.memory.InMemoryQueueRepository;
import discordgateway.infrastructure.redis.RedisGuildPlaybackLockManager;
import discordgateway.infrastructure.redis.RedisGuildStateRepository;
import discordgateway.infrastructure.redis.RedisMusicEventOutboxRepository;
import discordgateway.infrastructure.redis.RedisPlayerStateRepository;
import discordgateway.infrastructure.redis.RedisProcessedCommandRepository;
import discordgateway.infrastructure.redis.RedisQueueRepository;
import discordgateway.infrastructure.redis.RedisSupport;
import moe.kyokobot.libdave.NativeDaveFactory;
import moe.kyokobot.libdave.jda.LDJDADaveSessionFactory;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.audio.AudioModuleConfig;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;

import java.util.List;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({
        AppProperties.class,
        DiscordProperties.class,
        YouTubeProperties.class,
        RedisConnectionProperties.class,
        MessagingProperties.class,
        OperationsProperties.class
})
public class ApplicationFactory {

    @Bean
    public JdaRuntimeContext jdaRuntimeContext() {
        return new JdaRuntimeContext();
    }

    @Bean
    public DiscordReferenceResolver discordReferenceResolver(JdaRuntimeContext runtimeContext) {
        return new JdaDiscordReferenceResolver(runtimeContext);
    }

    @Bean
    public GuildStateRepository guildStateRepository(
            AppProperties appProperties,
            ObjectProvider<RedisSupport> redisSupportProvider
    ) {
        if ("redis".equalsIgnoreCase(appProperties.getStateStore())) {
            return new RedisGuildStateRepository(redisSupportProvider.getObject().pool());
        }
        return new InMemoryGuildStateRepository();
    }

    @Bean
    public QueueRepository queueRepository(
            AppProperties appProperties,
            ObjectProvider<RedisSupport> redisSupportProvider
    ) {
        if ("redis".equalsIgnoreCase(appProperties.getQueueStore())) {
            return new RedisQueueRepository(redisSupportProvider.getObject().pool());
        }
        return new InMemoryQueueRepository();
    }

    @Bean
    public PlayerStateRepository playerStateRepository(
            AppProperties appProperties,
            ObjectProvider<RedisSupport> redisSupportProvider
    ) {
        if ("redis".equalsIgnoreCase(appProperties.getPlayerStateStore())) {
            return new RedisPlayerStateRepository(redisSupportProvider.getObject().pool());
        }
        return new InMemoryPlayerStateRepository();
    }

    @Bean
    public ProcessedCommandRepository processedCommandRepository(
            AppProperties appProperties,
            ObjectProvider<RedisSupport> redisSupportProvider
    ) {
        if ("redis".equalsIgnoreCase(appProperties.getCommandDedupStore())) {
            return new RedisProcessedCommandRepository(redisSupportProvider.getObject().pool());
        }
        return new InMemoryProcessedCommandRepository();
    }

    @Bean
    public MusicEventOutboxRepository musicEventOutboxRepository(
            AppProperties appProperties,
            ObjectProvider<RedisSupport> redisSupportProvider,
            ObjectMapper objectMapper
    ) {
        if ("redis".equalsIgnoreCase(appProperties.getEventOutboxStore())) {
            return new RedisMusicEventOutboxRepository(redisSupportProvider.getObject().pool(), objectMapper);
        }
        return new InMemoryMusicEventOutboxRepository();
    }

    @Bean
    public GuildPlaybackLockManager guildPlaybackLockManager(
            AppProperties appProperties,
            QueueRepository queueRepository,
            ObjectProvider<RedisSupport> redisSupportProvider
    ) {
        if ("redis".equalsIgnoreCase(appProperties.getQueueStore())) {
            return new RedisGuildPlaybackLockManager(
                    redisSupportProvider.getObject().pool(),
                    appProperties.getGuildLockTtlMs()
            );
        }
        return new InMemoryGuildPlaybackLockManager();
    }

    @Bean
    @Lazy
    public RedisSupport redisSupport(RedisConnectionProperties properties) {
        return new RedisSupport(properties);
    }

    @Bean
    public MusicEventFactory musicEventFactory(AppProperties appProperties) {
        return new MusicEventFactory(appProperties);
    }

    @Bean
    public SpringMusicEventPublisher springMusicEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        return new SpringMusicEventPublisher(applicationEventPublisher);
    }

    @Bean
    @ConditionalOnProperty(prefix = "messaging", name = "event-transport", havingValue = "spring", matchIfMissing = true)
    public MusicEventPublisher musicEventPublisher(SpringMusicEventPublisher springMusicEventPublisher) {
        return springMusicEventPublisher;
    }

    @Bean
    public PlayerManager playerManager(
            QueueRepository queueRepository,
            PlayerStateRepository playerStateRepository,
            GuildPlaybackLockManager guildPlaybackLockManager,
            AppProperties appProperties,
            YouTubeProperties youTubeProperties,
            MusicEventPublisher musicEventPublisher,
            MusicEventFactory musicEventFactory
    ) {
        return new PlayerManager(
                queueRepository,
                playerStateRepository,
                guildPlaybackLockManager,
                appProperties,
                youTubeProperties,
                musicEventPublisher,
                musicEventFactory
        );
    }

    @Bean
    public PlaybackGateway playbackGateway(PlayerManager playerManager, QueueRepository queueRepository) {
        return new LavaPlayerPlaybackGateway(playerManager, queueRepository);
    }

    @Bean
    public VoiceGateway voiceGateway() {
        return new JdaVoiceGateway();
    }

    @Bean
    public MusicWorkerService musicWorkerService(
            DiscordReferenceResolver discordReferenceResolver,
            PlaybackGateway playbackGateway,
            VoiceGateway voiceGateway,
            GuildStateRepository guildStateRepository,
            PlayerStateRepository playerStateRepository,
            MusicEventPublisher musicEventPublisher,
            MusicEventFactory musicEventFactory
    ) {
        return new MusicWorkerService(
                discordReferenceResolver,
                playbackGateway,
                voiceGateway,
                guildStateRepository,
                playerStateRepository,
                musicEventPublisher,
                musicEventFactory
        );
    }

    @Bean
    @ConditionalOnMissingBean(MusicCommandBus.class)
    public MusicCommandBus musicCommandBus(
            MusicWorkerService musicWorkerService,
            MusicCommandMessageFactory musicCommandMessageFactory
    ) {
        return new InProcessMusicCommandBus(musicWorkerService, musicCommandMessageFactory);
    }

    @Bean
    public MusicApplicationService musicApplicationService(MusicCommandBus musicCommandBus) {
        return new MusicApplicationService(musicCommandBus);
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
    public PlayAutocompleteService playAutocompleteService(PlaybackGateway playbackGateway) {
        return new PlayAutocompleteService(playbackGateway);
    }

    @Bean
    @ConditionalOnAppRole({AppRole.GATEWAY, AppRole.ALL})
    public DiscordBotListener discordBotListener(
            MusicApplicationService musicApplicationService,
            PlayAutocompleteService playAutocompleteService
    ) {
        return new DiscordBotListener(
                musicApplicationService,
                playAutocompleteService
        );
    }

    @Bean
    @ConditionalOnAppRole({AppRole.GATEWAY, AppRole.ALL})
    public DiscordCommandRegistrationListener discordCommandRegistrationListener(
            DiscordProperties discordProperties
    ) {
        return new DiscordCommandRegistrationListener(discordProperties);
    }

    @Bean
    @ConditionalOnAppRole({AppRole.AUDIO_NODE, AppRole.ALL})
    public PlaybackRecoveryReadyListener playbackRecoveryReadyListener(
            PlaybackRecoveryService playbackRecoveryService
    ) {
        return new PlaybackRecoveryReadyListener(playbackRecoveryService);
    }

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnProperty(prefix = "ops", name = "command-dlq-replay-enabled", havingValue = "false", matchIfMissing = true)
    public JDA jda(
            DiscordProperties discordProperties,
            Environment environment,
            JdaRuntimeContext runtimeContext,
            ObjectProvider<List<ListenerAdapter>> listenerAdaptersProvider
    ) throws Exception {
        var daveFactory = new NativeDaveFactory();
        var daveSessionFactory = new LDJDADaveSessionFactory(daveFactory);

        AudioModuleConfig audioModuleConfig = new AudioModuleConfig()
                .withDaveSessionFactory(daveSessionFactory);

        String token = resolveDiscordToken(discordProperties, environment);
        List<ListenerAdapter> listenerAdapters = listenerAdaptersProvider.getIfAvailable(List::of);

        JDABuilder builder = JDABuilder.createDefault(
                        token,
                        GatewayIntent.GUILD_VOICE_STATES
                )
                .setAudioModuleConfig(audioModuleConfig);

        if (!listenerAdapters.isEmpty()) {
            builder.addEventListeners(listenerAdapters.toArray());
        }

        JDA jda = builder.build();

        runtimeContext.bind(jda);
        jda.awaitReady();
        return jda;
    }

    private String resolveDiscordToken(DiscordProperties discordProperties, Environment environment) {
        String configured = trimToNull(discordProperties.getToken());
        if (configured != null) {
            return configured;
        }

        String fallback = trimToNull(environment.getProperty("discord.token"));
        if (fallback != null) {
            return fallback;
        }

        fallback = trimToNull(environment.getProperty("DISCORD_TOKEN"));
        if (fallback != null) {
            return fallback;
        }

        fallback = trimToNull(environment.getProperty("token"));
        if (fallback != null) {
            return fallback;
        }

        fallback = trimToNull(environment.getProperty("TOKEN"));
        if (fallback != null) {
            return fallback;
        }

        throw new IllegalStateException(
                "Discord bot token is missing. Set property 'discord.token' or env 'DISCORD_TOKEN'/'TOKEN'."
        );
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
