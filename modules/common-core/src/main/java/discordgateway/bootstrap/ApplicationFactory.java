package discordgateway.bootstrap;

import discordgateway.application.DiscordReferenceResolver;
import discordgateway.application.MusicCommandBus;
import discordgateway.application.MusicWorkerService;
import discordgateway.application.VoiceSessionLifecycleService;
import discordgateway.application.event.MusicEventFactory;
import discordgateway.application.event.MusicEventPublisher;
import discordgateway.application.event.SpringMusicEventPublisher;
import discordgateway.audio.PlayerManager;
import discordgateway.domain.GuildPlaybackLockManager;
import discordgateway.domain.GuildStateRepository;
import discordgateway.domain.PlayerStateRepository;
import discordgateway.domain.ProcessedCommandRepository;
import discordgateway.domain.QueueRepository;
import discordgateway.infrastructure.audio.JdaVoiceGateway;
import discordgateway.infrastructure.audio.LavaPlayerPlaybackGateway;
import discordgateway.infrastructure.audio.PlaybackGateway;
import discordgateway.infrastructure.audio.VoiceGateway;
import discordgateway.infrastructure.discord.JdaDiscordReferenceResolver;
import discordgateway.infrastructure.discord.JdaRuntimeContext;
import discordgateway.infrastructure.redis.RedisGuildPlaybackLockManager;
import discordgateway.infrastructure.redis.RedisGuildStateRepository;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;

import java.util.List;
import java.util.Optional;

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

    private static final Logger log = LoggerFactory.getLogger(ApplicationFactory.class);

    @Bean
    public JdaRuntimeContext jdaRuntimeContext() {
        return new JdaRuntimeContext();
    }

    @Bean
    public DiscordReferenceResolver discordReferenceResolver(JdaRuntimeContext runtimeContext) {
        return new JdaDiscordReferenceResolver(runtimeContext);
    }

    @Bean
    public GuildStateRepository guildStateRepository(RedisSupport redisSupport) {
        return new RedisGuildStateRepository(redisSupport.pool());
    }

    @Bean
    public QueueRepository queueRepository(RedisSupport redisSupport) {
        return new RedisQueueRepository(redisSupport.pool());
    }

    @Bean
    public PlayerStateRepository playerStateRepository(RedisSupport redisSupport) {
        return new RedisPlayerStateRepository(redisSupport.pool());
    }

    @Bean
    public ProcessedCommandRepository processedCommandRepository(RedisSupport redisSupport) {
        return new RedisProcessedCommandRepository(redisSupport.pool());
    }

    @Bean
    public GuildPlaybackLockManager guildPlaybackLockManager(
            AppProperties appProperties,
            RedisSupport redisSupport
    ) {
        return new RedisGuildPlaybackLockManager(
                redisSupport.pool(),
                appProperties.getGuildLockTtlMs()
        );
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
    public MusicEventPublisher musicEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        return new SpringMusicEventPublisher(applicationEventPublisher);
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
            VoiceSessionLifecycleService voiceSessionLifecycleService,
            MusicEventPublisher musicEventPublisher,
            MusicEventFactory musicEventFactory
    ) {
        return new MusicWorkerService(
                discordReferenceResolver,
                playbackGateway,
                voiceGateway,
                guildStateRepository,
                playerStateRepository,
                voiceSessionLifecycleService,
                musicEventPublisher,
                musicEventFactory
        );
    }

    @Bean
    public VoiceSessionLifecycleService voiceSessionLifecycleService(
            PlaybackGateway playbackGateway,
            QueueRepository queueRepository,
            VoiceGateway voiceGateway,
            GuildStateRepository guildStateRepository,
            PlayerStateRepository playerStateRepository,
            MusicEventPublisher musicEventPublisher,
            MusicEventFactory musicEventFactory
    ) {
        return new VoiceSessionLifecycleService(
                playbackGateway,
                queueRepository,
                voiceGateway,
                guildStateRepository,
                playerStateRepository,
                musicEventPublisher,
                musicEventFactory
        );
    }

    @Bean
    public ApplicationRunner startupConfigurationLogger(
            AppProperties appProperties,
            Environment environment,
            ObjectProvider<MusicCommandBus> musicCommandBusProvider,
            MusicEventPublisher musicEventPublisher
    ) {
        return args -> log.atInfo()
                .addKeyValue("application", environment.getProperty("spring.application.name", "unknown"))
                .addKeyValue("node", appProperties.getNodeName())
                .addKeyValue(
                        "commandBus",
                        Optional.ofNullable(musicCommandBusProvider.getIfAvailable())
                                .map(bus -> bus.getClass().getSimpleName())
                                .orElse("none")
                )
                .addKeyValue("eventPublisher", musicEventPublisher.getClass().getSimpleName())
                .log("startup-config");
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
