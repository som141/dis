package discordgateway.gateway.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import discordgateway.common.bootstrap.AppProperties;
import discordgateway.common.bootstrap.DiscordProperties;
import discordgateway.common.bootstrap.MessagingProperties;
import discordgateway.common.command.MusicCommandBus;
import discordgateway.common.command.MusicCommandMessageFactory;
import discordgateway.gateway.application.MusicApplicationService;
import discordgateway.gateway.application.PlayAutocompleteService;
import discordgateway.gateway.application.StockApplicationService;
import discordgateway.gateway.interaction.InteractionResponseEditor;
import discordgateway.gateway.interaction.JdaInteractionResponseEditor;
import discordgateway.gateway.interaction.PendingInteractionRepository;
import discordgateway.gateway.interaction.RedisPendingInteractionRepository;
import discordgateway.gateway.messaging.RabbitMusicCommandResultListener;
import discordgateway.gateway.messaging.RabbitStockCommandBus;
import discordgateway.gateway.messaging.RabbitStockCommandResultListener;
import discordgateway.gateway.presentation.discord.DiscordBotListener;
import discordgateway.gateway.presentation.discord.DiscordCommandRegistrationListener;
import discordgateway.infra.audio.PlaybackGateway;
import discordgateway.infra.messaging.rabbit.RabbitMusicCommandBus;
import discordgateway.infra.redis.RedisSupport;
import discordgateway.stock.messaging.StockCommandBus;
import discordgateway.stock.messaging.StockCommandMessageFactory;
import discordgateway.stock.messaging.StockMessagingProperties;
import net.dv8tion.jda.api.JDA;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(StockMessagingProperties.class)
public class GatewayComponentConfiguration {

    @Bean
    public Queue gatewayCommandResultQueue(
            AppProperties appProperties,
            MessagingProperties messagingProperties
    ) {
        return QueueBuilder.durable(
                messagingProperties.commandResultQueue(appProperties.getNodeName())
        ).build();
    }

    @Bean
    public Declarables gatewayCommandResultDeclarables(
            Queue gatewayCommandResultQueue,
            AppProperties appProperties,
            MessagingProperties messagingProperties
    ) {
        return new Declarables(
                gatewayCommandResultQueue,
                BindingBuilder.bind(gatewayCommandResultQueue)
                        .to(new DirectExchange(
                                messagingProperties.getCommandResultExchange(),
                                true,
                                false
                        ))
                        .with(messagingProperties.commandResultRoutingKey(appProperties.getNodeName()))
        );
    }

    @Bean
    public Queue gatewayStockCommandResultQueue(
            AppProperties appProperties,
            StockMessagingProperties stockMessagingProperties
    ) {
        return QueueBuilder.durable(
                stockMessagingProperties.commandResultQueue(appProperties.getNodeName())
        ).build();
    }

    @Bean
    public Declarables gatewayStockCommandResultDeclarables(
            Queue gatewayStockCommandResultQueue,
            AppProperties appProperties,
            StockMessagingProperties stockMessagingProperties
    ) {
        return new Declarables(
                gatewayStockCommandResultQueue,
                BindingBuilder.bind(gatewayStockCommandResultQueue)
                        .to(new DirectExchange(
                                stockMessagingProperties.getCommandResultExchange(),
                                true,
                                false
                        ))
                        .with(stockMessagingProperties.commandResultRoutingKey(appProperties.getNodeName()))
        );
    }

    @Bean
    public MusicCommandBus musicCommandBus(
            RabbitTemplate rabbitTemplate,
            MessagingProperties messagingProperties
    ) {
        return new RabbitMusicCommandBus(rabbitTemplate, messagingProperties);
    }

    @Bean
    public MusicApplicationService musicApplicationService(
            MusicCommandBus musicCommandBus,
            MusicCommandMessageFactory musicCommandMessageFactory
    ) {
        return new MusicApplicationService(musicCommandBus, musicCommandMessageFactory);
    }

    @Bean
    public StockCommandMessageFactory stockCommandMessageFactory(AppProperties appProperties) {
        return new StockCommandMessageFactory(appProperties.getNodeName());
    }

    @Bean
    public StockCommandBus stockCommandBus(
            RabbitTemplate rabbitTemplate,
            StockMessagingProperties stockMessagingProperties
    ) {
        return new RabbitStockCommandBus(rabbitTemplate, stockMessagingProperties);
    }

    @Bean
    public StockApplicationService stockApplicationService(
            StockCommandBus stockCommandBus,
            StockCommandMessageFactory stockCommandMessageFactory
    ) {
        return new StockApplicationService(stockCommandBus, stockCommandMessageFactory);
    }

    @Bean
    public PendingInteractionRepository pendingInteractionRepository(
            RedisSupport redisSupport,
            ObjectMapper objectMapper
    ) {
        return new RedisPendingInteractionRepository(redisSupport.pool(), objectMapper);
    }

    @Bean
    public InteractionResponseEditor interactionResponseEditor(JDA jda) {
        return new JdaInteractionResponseEditor(jda);
    }

    @Bean
    public RabbitMusicCommandResultListener rabbitMusicCommandResultListener(
            PendingInteractionRepository pendingInteractionRepository,
            InteractionResponseEditor interactionResponseEditor
    ) {
        return new RabbitMusicCommandResultListener(
                pendingInteractionRepository,
                interactionResponseEditor
        );
    }

    @Bean
    public RabbitStockCommandResultListener rabbitStockCommandResultListener(
            PendingInteractionRepository pendingInteractionRepository,
            InteractionResponseEditor interactionResponseEditor
    ) {
        return new RabbitStockCommandResultListener(
                pendingInteractionRepository,
                interactionResponseEditor
        );
    }

    @Bean
    public PlayAutocompleteService playAutocompleteService(PlaybackGateway playbackGateway) {
        return new PlayAutocompleteService(playbackGateway);
    }

    @Bean
    public DiscordBotListener discordBotListener(
            MusicApplicationService musicApplicationService,
            StockApplicationService stockApplicationService,
            PlayAutocompleteService playAutocompleteService,
            PendingInteractionRepository pendingInteractionRepository
    ) {
        return new DiscordBotListener(
                musicApplicationService,
                stockApplicationService,
                playAutocompleteService,
                pendingInteractionRepository
        );
    }

    @Bean
    public DiscordCommandRegistrationListener discordCommandRegistrationListener(
            DiscordProperties discordProperties
    ) {
        return new DiscordCommandRegistrationListener(discordProperties);
    }
}
