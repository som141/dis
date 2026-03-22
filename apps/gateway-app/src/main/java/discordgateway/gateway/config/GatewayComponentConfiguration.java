package discordgateway.gateway.config;

import discordgateway.gateway.application.MusicApplicationService;
import discordgateway.common.command.MusicCommandBus;
import discordgateway.common.command.MusicCommandMessageFactory;
import discordgateway.gateway.application.PlayAutocompleteService;
import discordgateway.common.bootstrap.MessagingProperties;
import discordgateway.gateway.presentation.discord.DiscordBotListener;
import discordgateway.gateway.presentation.discord.DiscordCommandRegistrationListener;
import discordgateway.common.bootstrap.DiscordProperties;
import discordgateway.infra.audio.PlaybackGateway;
import discordgateway.infra.messaging.rabbit.RabbitMusicCommandBus;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration(proxyBeanMethods = false)
public class GatewayComponentConfiguration {

    @Bean(destroyMethod = "close")
    public ExecutorService rabbitCommandBusExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Bean
    public MusicCommandBus musicCommandBus(
            RabbitTemplate rabbitTemplate,
            MessagingProperties messagingProperties,
            MusicCommandMessageFactory musicCommandMessageFactory,
            ExecutorService rabbitCommandBusExecutor
    ) {
        return new RabbitMusicCommandBus(
                rabbitTemplate,
                messagingProperties,
                musicCommandMessageFactory,
                rabbitCommandBusExecutor
        );
    }

    @Bean
    public MusicApplicationService musicApplicationService(MusicCommandBus musicCommandBus) {
        return new MusicApplicationService(musicCommandBus);
    }

    @Bean
    public PlayAutocompleteService playAutocompleteService(PlaybackGateway playbackGateway) {
        return new PlayAutocompleteService(playbackGateway);
    }

    @Bean
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
    public DiscordCommandRegistrationListener discordCommandRegistrationListener(
            DiscordProperties discordProperties
    ) {
        return new DiscordCommandRegistrationListener(discordProperties);
    }
}
