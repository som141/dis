package discordgateway.stocknode.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import discordgateway.stock.messaging.StockMessagingProperties;
import discordgateway.stocknode.messaging.StockCommandListener;
import discordgateway.stocknode.messaging.StockCommandResultPublisher;
import discordgateway.stocknode.application.StockCommandApplicationService;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableRabbit
public class StockRabbitMessagingConfiguration {

    @Bean
    public Jackson2JsonMessageConverter stockRabbitMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    @ConditionalOnProperty(prefix = "stock.messaging", name = "enabled", havingValue = "true", matchIfMissing = true)
    public Declarables stockRabbitDeclarables(StockMessagingProperties messagingProperties) {
        DirectExchange commandExchange = new DirectExchange(messagingProperties.getCommandExchange(), true, false);
        DirectExchange resultExchange = new DirectExchange(messagingProperties.getCommandResultExchange(), true, false);
        Queue commandQueue = QueueBuilder.durable(messagingProperties.getCommandQueue()).build();
        return new Declarables(
                commandExchange,
                resultExchange,
                commandQueue,
                BindingBuilder.bind(commandQueue)
                        .to(commandExchange)
                        .with(messagingProperties.getCommandRoutingKey())
        );
    }

    @Bean
    @ConditionalOnProperty(prefix = "stock.messaging", name = "enabled", havingValue = "true", matchIfMissing = true)
    public StockCommandResultPublisher stockCommandResultPublisher(
            RabbitTemplate rabbitTemplate,
            StockMessagingProperties messagingProperties
    ) {
        return new StockCommandResultPublisher(rabbitTemplate, messagingProperties);
    }

    @Bean
    @ConditionalOnProperty(prefix = "stock.messaging", name = "listener-enabled", havingValue = "true", matchIfMissing = true)
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
