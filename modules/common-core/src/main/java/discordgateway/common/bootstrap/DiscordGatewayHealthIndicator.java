package discordgateway.common.bootstrap;

import net.dv8tion.jda.api.JDA;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class DiscordGatewayHealthIndicator implements HealthIndicator {

    private final ObjectProvider<JDA> jdaProvider;

    public DiscordGatewayHealthIndicator(ObjectProvider<JDA> jdaProvider) {
        this.jdaProvider = jdaProvider;
    }

    @Override
    public Health health() {
        JDA jda = jdaProvider.getIfAvailable();
        if (jda == null) {
            return Health.down()
                    .withDetail("reason", "jda_not_initialized")
                    .build();
        }

        return switch (jda.getStatus()) {
            case CONNECTED -> Health.up()
                    .withDetail("status", jda.getStatus().name())
                    .build();
            default -> Health.down()
                    .withDetail("status", jda.getStatus().name())
                    .build();
        };
    }
}
