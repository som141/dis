package discordgateway.gateway;

import discordgateway.application.MusicApplicationService;
import discordgateway.application.MusicCommandBus;
import discordgateway.application.MusicCommandMessageFactory;
import discordgateway.application.PlayAutocompleteService;
import discordgateway.bootstrap.MessagingProperties;
import discordgateway.discord.DiscordBotListener;
import discordgateway.discord.DiscordCommandRegistrationListener;
import discordgateway.bootstrap.DiscordProperties;
import discordgateway.infrastructure.audio.PlaybackGateway;
import discordgateway.infrastructure.messaging.rabbit.RabbitMusicCommandBus;
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
