package discordgateway.gateway;

import discordgateway.application.MusicApplicationService;
import discordgateway.application.MusicCommandBus;
import discordgateway.application.PlayAutocompleteService;
import discordgateway.discord.DiscordBotListener;
import discordgateway.discord.DiscordCommandRegistrationListener;
import discordgateway.bootstrap.DiscordProperties;
import discordgateway.infrastructure.audio.PlaybackGateway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class GatewayComponentConfiguration {

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
