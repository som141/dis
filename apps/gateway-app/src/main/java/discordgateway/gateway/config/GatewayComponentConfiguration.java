package discordgateway.gateway.config;

import discordgateway.common.bootstrap.AppProperties;
import discordgateway.common.bootstrap.DiscordProperties;
import discordgateway.common.bootstrap.MessagingProperties;
import discordgateway.common.command.MusicCommandBus;
import discordgateway.common.command.MusicCommandMessageFactory;
import discordgateway.gateway.application.MusicApplicationService;
import discordgateway.gateway.application.PlayAutocompleteService;
import discordgateway.gateway.interaction.PendingInteractionRepository;
import discordgateway.gateway.interaction.RedisPendingInteractionRepository;
import discordgateway.gateway.messaging.RabbitMusicCommandResultListener;
import discordgateway.gateway.presentation.discord.DiscordBotListener;
import discordgateway.gateway.presentation.discord.DiscordCommandRegistrationListener;
import discordgateway.infra.audio.PlaybackGateway;
import discordgateway.infra.messaging.rabbit.RabbitMusicCommandBus;
import discordgateway.infra.redis.RedisSupport;
import net.dv8tion.jda.api.JDA;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
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
                        .to(new org.springframework.amqp.core.DirectExchange(
                                messagingProperties.getCommandResultExchange(),
                                true,
                                false
                        ))
                        .with(messagingProperties.commandResultRoutingKey(appProperties.getNodeName()))
        );
    }

    @Bean
    public MusicCommandBus musicCommandBus(
            RabbitTemplate rabbitTemplate,
            MessagingProperties messagingProperties
    ) {
        return new RabbitMusicCommandBus(
                rabbitTemplate,
                messagingProperties
        );
    }

    @Bean
    public MusicApplicationService musicApplicationService(
            MusicCommandBus musicCommandBus,
            MusicCommandMessageFactory musicCommandMessageFactory
    ) {
        return new MusicApplicationService(
                musicCommandBus,
                musicCommandMessageFactory
        );
    }

    @Bean
    public PendingInteractionRepository pendingInteractionRepository(
            RedisSupport redisSupport,
            ObjectMapper objectMapper
    ) {
        return new RedisPendingInteractionRepository(redisSupport.pool(), objectMapper);
    }

    @Bean
    public RabbitMusicCommandResultListener rabbitMusicCommandResultListener(
            PendingInteractionRepository pendingInteractionRepository,
            JDA jda
    ) {
        return new RabbitMusicCommandResultListener(pendingInteractionRepository, jda);
    }

    @Bean
    public PlayAutocompleteService playAutocompleteService(PlaybackGateway playbackGateway) {
        return new PlayAutocompleteService(playbackGateway);
    }

    @Bean
    public DiscordBotListener discordBotListener(
            MusicApplicationService musicApplicationService,
            PlayAutocompleteService playAutocompleteService,
            PendingInteractionRepository pendingInteractionRepository
    ) {
        return new DiscordBotListener(
                musicApplicationService,
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
