package discordgateway.infrastructure.messaging.rabbit;

import discordgateway.application.event.MusicEvent;
import discordgateway.bootstrap.MessagingProperties;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class RabbitMusicEventSender {

    private final RabbitTemplate rabbitTemplate;
    private final MessagingProperties messagingProperties;

    public RabbitMusicEventSender(RabbitTemplate rabbitTemplate, MessagingProperties messagingProperties) {
        this.rabbitTemplate = rabbitTemplate;
        this.messagingProperties = messagingProperties;
        this.rabbitTemplate.setMandatory(true);
    }

    public void send(MusicEvent event) {
        String exchange = messagingProperties.getEventExchange();
        String routingKey = toRoutingKey(event);
        CorrelationData correlationData = new CorrelationData(event.eventId());

        rabbitTemplate.convertAndSend(
                exchange,
                routingKey,
                event,
                correlationData
        );

        awaitBrokerAcceptance(event, exchange, routingKey, correlationData);
    }

    private String toRoutingKey(MusicEvent event) {
        return messagingProperties.getEventRoutingKeyPrefix()
                + "."
                + event.guildId()
                + "."
                + event.eventType();
    }

    private void awaitBrokerAcceptance(
            MusicEvent event,
            String exchange,
            String routingKey,
            CorrelationData correlationData
    ) {
        CorrelationData.Confirm confirm = awaitConfirm(event, exchange, routingKey, correlationData);
        ReturnedMessage returned = correlationData.getReturned();
        if (returned != null) {
            throw new IllegalStateException(
                    "RabbitMQ returned event. eventId=" + event.eventId()
                            + ", type=" + event.eventType()
                            + ", guild=" + event.guildId()
                            + ", exchange=" + returned.getExchange()
                            + ", routingKey=" + returned.getRoutingKey()
                            + ", replyCode=" + returned.getReplyCode()
                            + ", replyText=" + returned.getReplyText()
            );
        }

        if (!confirm.isAck()) {
            throw new IllegalStateException(
                    "RabbitMQ publisher confirm nack. eventId=" + event.eventId()
                            + ", type=" + event.eventType()
                            + ", guild=" + event.guildId()
                            + ", exchange=" + exchange
                            + ", routingKey=" + routingKey
                            + ", reason=" + confirm.getReason()
            );
        }
    }

    private CorrelationData.Confirm awaitConfirm(
            MusicEvent event,
            String exchange,
            String routingKey,
            CorrelationData correlationData
    ) {
        try {
            CorrelationData.Confirm confirm = correlationData.getFuture().get(
                    messagingProperties.getEventPublishConfirmTimeoutMs(),
                    TimeUnit.MILLISECONDS
            );
            if (confirm == null) {
                throw new IllegalStateException(
                        "RabbitMQ publisher confirm returned null. eventId=" + event.eventId()
                                + ", exchange=" + exchange
                                + ", routingKey=" + routingKey
                );
            }
            return confirm;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Interrupted while waiting for RabbitMQ publisher confirm. eventId=" + event.eventId(),
                    e
            );
        } catch (TimeoutException e) {
            throw new IllegalStateException(
                    "Timed out waiting for RabbitMQ publisher confirm. eventId=" + event.eventId()
                            + ", exchange=" + exchange
                            + ", routingKey=" + routingKey
                            + ", timeoutMs=" + messagingProperties.getEventPublishConfirmTimeoutMs(),
                    e
            );
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new IllegalStateException(
                    "RabbitMQ publisher confirm future failed. eventId=" + event.eventId()
                            + ", exchange=" + exchange
                            + ", routingKey=" + routingKey,
                    cause
            );
        }
    }
}
