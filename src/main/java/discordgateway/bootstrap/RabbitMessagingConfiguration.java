package discordgateway.bootstrap;

import com.fasterxml.jackson.databind.ObjectMapper;
import discordgateway.application.MusicCommandBus;
import discordgateway.application.MusicCommandMessageFactory;
import discordgateway.application.MusicWorkerService;
import discordgateway.application.event.CompositeMusicEventPublisher;
import discordgateway.application.event.MusicEventPublisher;
import discordgateway.application.event.SpringMusicEventPublisher;
import discordgateway.domain.MusicEventOutboxRepository;
import discordgateway.domain.ProcessedCommandRepository;
import discordgateway.infrastructure.messaging.rabbit.CommandDlqReplayService;
import discordgateway.infrastructure.messaging.rabbit.RabbitMusicCommandBus;
import discordgateway.infrastructure.messaging.rabbit.RabbitMusicCommandListener;
import discordgateway.infrastructure.messaging.rabbit.MusicEventOutboxRelay;
import discordgateway.infrastructure.messaging.rabbit.RabbitMusicEventSender;
import discordgateway.infrastructure.messaging.rabbit.RabbitMusicEventPublisher;
import org.springframework.boot.ApplicationRunner;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = "messaging", name = "command-transport", havingValue = "rabbitmq")
    public ExecutorService rabbitCommandBusExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Bean
    @ConditionalOnProperty(prefix = "messaging", name = "command-transport", havingValue = "rabbitmq")
    public MusicCommandBus rabbitMusicCommandBus(
            RabbitTemplate rabbitTemplate,
            MessagingProperties messagingProperties,
            MusicCommandMessageFactory messageFactory,
            ExecutorService rabbitCommandBusExecutor
    ) {
        return new RabbitMusicCommandBus(
                rabbitTemplate,
                messagingProperties,
                messageFactory,
                rabbitCommandBusExecutor
        );
    }

    @Bean
    @ConditionalOnProperty(prefix = "messaging", name = "command-transport", havingValue = "rabbitmq")
    @ConditionalOnAppRole({AppRole.AUDIO_NODE, AppRole.ALL})
    @ConditionalOnProperty(prefix = "ops", name = "command-dlq-replay-enabled", havingValue = "false", matchIfMissing = true)
    public RabbitMusicCommandListener rabbitMusicCommandListener(
            MusicWorkerService musicWorkerService,
            ProcessedCommandRepository processedCommandRepository,
            MessagingProperties messagingProperties
    ) {
        return new RabbitMusicCommandListener(
                musicWorkerService,
                processedCommandRepository,
                messagingProperties
        );
    }

    @Bean
    @ConditionalOnProperty(prefix = "messaging", name = "command-transport", havingValue = "rabbitmq")
    public Declarables rabbitCommandDeclarables(MessagingProperties messagingProperties) {
        DirectExchange exchange = new DirectExchange(messagingProperties.getCommandExchange(), true, false);
        DirectExchange deadLetterExchange = new DirectExchange(
                messagingProperties.getCommandDeadLetterExchange(),
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
    @ConditionalOnProperty(prefix = "messaging", name = "event-transport", havingValue = "rabbitmq")
    public RabbitMusicEventSender rabbitMusicEventSender(
            RabbitTemplate rabbitTemplate,
            MessagingProperties messagingProperties
    ) {
        return new RabbitMusicEventSender(rabbitTemplate, messagingProperties);
    }

    @Bean
    @ConditionalOnProperty(prefix = "messaging", name = "event-transport", havingValue = "rabbitmq")
    public MusicEventPublisher rabbitMusicEventPublisher(
            RabbitMusicEventSender rabbitMusicEventSender,
            MusicEventOutboxRepository musicEventOutboxRepository,
            MessagingProperties messagingProperties,
            SpringMusicEventPublisher springMusicEventPublisher
    ) {
        return new CompositeMusicEventPublisher(
                List.of(
                        springMusicEventPublisher,
                        new RabbitMusicEventPublisher(
                                rabbitMusicEventSender,
                                musicEventOutboxRepository,
                                messagingProperties
                        )
                )
        );
    }

    @Bean
    @ConditionalOnProperty(prefix = "messaging", name = "event-transport", havingValue = "rabbitmq")
    @ConditionalOnProperty(prefix = "ops", name = "command-dlq-replay-enabled", havingValue = "false", matchIfMissing = true)
    public MusicEventOutboxRelay musicEventOutboxRelay(
            RabbitMusicEventSender rabbitMusicEventSender,
            MusicEventOutboxRepository musicEventOutboxRepository,
            MessagingProperties messagingProperties,
            AppProperties appProperties
    ) {
        return new MusicEventOutboxRelay(
                rabbitMusicEventSender,
                musicEventOutboxRepository,
                messagingProperties,
                appProperties
        );
    }

    @Bean
    @ConditionalOnProperty(prefix = "messaging", name = "event-transport", havingValue = "rabbitmq")
    public Declarables rabbitEventDeclarables(MessagingProperties messagingProperties) {
        TopicExchange exchange = new TopicExchange(messagingProperties.getEventExchange(), true, false);
        return new Declarables(exchange);
    }

    @Bean
    @ConditionalOnProperty(prefix = "messaging", name = "command-transport", havingValue = "rabbitmq")
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
    @ConditionalOnProperty(prefix = "messaging", name = "command-transport", havingValue = "rabbitmq")
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
