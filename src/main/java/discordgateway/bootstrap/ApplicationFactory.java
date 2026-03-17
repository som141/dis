package discordgateway.bootstrap;

import discordgateway.application.MusicApplicationService;
import discordgateway.application.PlayAutocompleteService;
import discordgateway.discord.DiscordBotListener;
import discordgateway.domain.GuildStateRepository;
import discordgateway.domain.QueueRepository;
import discordgateway.infrastructure.audio.JdaVoiceGateway;
import discordgateway.infrastructure.audio.LavaPlayerPlaybackGateway;
import discordgateway.infrastructure.memory.InMemoryGuildStateRepository;
import discordgateway.infrastructure.memory.InMemoryQueueRepository;
import discordgateway.infrastructure.redis.RedisGuildStateRepository;
import discordgateway.infrastructure.redis.RedisQueueRepository;
import discordgateway.infrastructure.redis.RedisSupport;

public class ApplicationFactory implements AutoCloseable {

    private RedisSupport redisSupport;

    public DiscordBotListener createDiscordBotListener() {
        GuildStateRepository guildStateRepository = createGuildStateRepository();
        QueueRepository queueRepository = createQueueRepository();

        var playbackGateway = new LavaPlayerPlaybackGateway(queueRepository);
        var voiceGateway = new JdaVoiceGateway();

        var musicApplicationService = new MusicApplicationService(
                playbackGateway,
                voiceGateway,
                guildStateRepository
        );

        var autoCompleteService = new PlayAutocompleteService(playbackGateway);

        return new DiscordBotListener(musicApplicationService, autoCompleteService);
    }

    private GuildStateRepository createGuildStateRepository() {
        String storeType = System.getenv().getOrDefault("APP_STATE_STORE", "memory");

        if ("redis".equalsIgnoreCase(storeType)) {
            return new RedisGuildStateRepository(redis().pool());
        }

        return new InMemoryGuildStateRepository();
    }

    private QueueRepository createQueueRepository() {
        String queueStore = System.getenv().getOrDefault(
                "APP_QUEUE_STORE",
                System.getenv().getOrDefault("APP_STATE_STORE", "memory")
        );

        if ("redis".equalsIgnoreCase(queueStore)) {
            return new RedisQueueRepository(redis().pool());
        }

        return new InMemoryQueueRepository();
    }

    private RedisSupport redis() {
        if (redisSupport == null) {
            redisSupport = new RedisSupport();
        }
        return redisSupport;
    }

    @Override
    public void close() {
        if (redisSupport != null) {
            redisSupport.close();
        }
    }
}