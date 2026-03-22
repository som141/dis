package discordgateway.common.bootstrap;

import com.fasterxml.jackson.databind.ObjectMapper;
import discordgateway.common.command.MusicCommandMessageFactory;
import discordgateway.infra.messaging.rabbit.CommandDlqReplayService;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableRabbit
public class RabbitMessagingConfiguration {

    @Bean
    public Jackson2JsonMessageConverter rabbitMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    public MusicCommandMessageFactory musicCommandMessageFactory(AppProperties appProperties) {
        return new MusicCommandMessageFactory(appProperties);
    }

    @Bean
    public Declarables rabbitCommandDeclarables(MessagingProperties messagingProperties) {
        DirectExchange exchange = new DirectExchange(messagingProperties.getCommandExchange(), true, false);
        DirectExchange deadLetterExchange = new DirectExchange(
                messagingProperties.getCommandDeadLetterExchange(),
                true,
                false
        );
        DirectExchange resultExchange = new DirectExchange(
                messagingProperties.getCommandResultExchange(),
                true,
                false
        );
        Queue queue = QueueBuilder.durable(messagingProperties.getCommandQueue())
                .deadLetterExchange(messagingProperties.getCommandDeadLetterExchange())
                .deadLetterRoutingKey(messagingProperties.getCommandDeadLetterRoutingKey())
                .build();
        Queue deadLetterQueue = QueueBuilder.durable(messagingProperties.getCommandDeadLetterQueue()).build();

        return new Declarables(
                exchange,
                deadLetterExchange,
                resultExchange,
                queue,
                deadLetterQueue,
                BindingBuilder.bind(queue)
                        .to(exchange)
                        .with(messagingProperties.getCommandRoutingKey()),
                BindingBuilder.bind(deadLetterQueue)
                        .to(deadLetterExchange)
                        .with(messagingProperties.getCommandDeadLetterRoutingKey())
        );
    }

    @Bean
    public CommandDlqReplayService commandDlqReplayService(
            RabbitTemplate rabbitTemplate,
            ObjectMapper objectMapper,
            MessagingProperties messagingProperties,
            AppProperties appProperties
    ) {
        return new CommandDlqReplayService(
                rabbitTemplate,
                objectMapper,
                messagingProperties,
                appProperties
        );
    }

    @Bean
    @ConditionalOnProperty(prefix = "ops", name = "command-dlq-replay-enabled", havingValue = "true")
    public ApplicationRunner commandDlqReplayRunner(
            CommandDlqReplayService commandDlqReplayService,
            OperationsProperties operationsProperties,
            org.springframework.context.ConfigurableApplicationContext applicationContext
    ) {
        return new CommandDlqReplayRunner(
                commandDlqReplayService,
                operationsProperties,
                applicationContext
        );
    }
}
