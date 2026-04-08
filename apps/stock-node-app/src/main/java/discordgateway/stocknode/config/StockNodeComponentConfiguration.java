package discordgateway.stocknode.config;

import discordgateway.stocknode.application.StockCommandApplicationService;
import discordgateway.stocknode.bootstrap.StockNodeMessagingProperties;
import discordgateway.stocknode.bootstrap.StockNodeStorageProperties;
import discordgateway.stocknode.messaging.StockCommandListener;
import discordgateway.stocknode.messaging.StockCommandResultPublisher;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({
        StockNodeMessagingProperties.class,
        StockNodeStorageProperties.class
})
public class StockNodeComponentConfiguration {

    @Bean
    public StockCommandApplicationService stockCommandApplicationService() {
        return new StockCommandApplicationService();
    }

    @Bean
    public StockCommandResultPublisher stockCommandResultPublisher() {
        return new StockCommandResultPublisher();
    }

    @Bean
    public StockCommandListener stockCommandListener(
            StockCommandApplicationService stockCommandApplicationService,
            StockCommandResultPublisher stockCommandResultPublisher
    ) {
        return new StockCommandListener(
                stockCommandApplicationService,
                stockCommandResultPublisher
        );
    }
}
