package discordgateway.infra.messaging.rabbit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.GetResponse;
import discordgateway.common.command.MusicCommandEnvelope;
import discordgateway.common.bootstrap.AppProperties;
import discordgateway.common.bootstrap.MessagingProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

public class CommandDlqReplayService {

    private static final Logger log = LoggerFactory.getLogger(CommandDlqReplayService.class);
    private static final long REPLAY_CONFIRM_TIMEOUT_MS = 5_000L;

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final MessagingProperties messagingProperties;
    private final AppProperties appProperties;

    public CommandDlqReplayService(
            RabbitTemplate rabbitTemplate,
            ObjectMapper objectMapper,
            MessagingProperties messagingProperties,
            AppProperties appProperties
    ) {
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.messagingProperties = messagingProperties;
        this.appProperties = appProperties;
    }

    public CommandDlqReplayReport replay(int maxMessages) {
        if (maxMessages <= 0) {
            return new CommandDlqReplayReport(0, 0, false);
        }

        return rabbitTemplate.execute(channel -> {
            int replayed = 0;
            int failed = 0;

            channel.confirmSelect();

            while (replayed < maxMessages) {
                GetResponse response = channel.basicGet(messagingProperties.getCommandDeadLetterQueue(), false);
                if (response == null) {
                    return new CommandDlqReplayReport(replayed, failed, false);
                }

                long deliveryTag = response.getEnvelope().getDeliveryTag();
                try {
                    MusicCommandEnvelope envelope = objectMapper.readValue(response.getBody(), MusicCommandEnvelope.class);
                    byte[] payload = objectMapper.writeValueAsBytes(envelope);

                    channel.basicPublish(
                            messagingProperties.getCommandExchange(),
                            messagingProperties.getCommandRoutingKey(),
                            buildReplayProperties(envelope),
                            payload
                    );
                    channel.waitForConfirmsOrDie(REPLAY_CONFIRM_TIMEOUT_MS);
                    channel.basicAck(deliveryTag, false);

                    replayed++;
                    log.info(
                            "command-dlq replayed commandId={} producer={} targetNode={} type={} guild={}",
                            envelope.message().commandId(),
                            envelope.message().producer(),
                            envelope.responseTargetNode(),
                            envelope.message().command().getClass().getSimpleName(),
                            envelope.message().command().guildId()
                    );
                } catch (Exception e) {
                    failed++;
                    channel.basicNack(deliveryTag, false, true);
                    log.warn("command-dlq replay failed. message returned to dlq", e);
                    return new CommandDlqReplayReport(replayed, failed, false);
                }
            }

            return new CommandDlqReplayReport(replayed, failed, true);
        });
    }

    private AMQP.BasicProperties buildReplayProperties(MusicCommandEnvelope envelope) {
        Map<String, Object> headers = new LinkedHashMap<>();
        headers.put("x-replayed-from-dlq", true);
        headers.put("x-replay-node", appProperties.getNodeName());
        headers.put("x-command-id", envelope.message().commandId());
        headers.put("x-schema-version", envelope.message().schemaVersion());

        return new AMQP.BasicProperties.Builder()
                .contentType("application/json")
                .contentEncoding(StandardCharsets.UTF_8.name())
                .deliveryMode(2)
                .messageId(envelope.message().commandId())
                .timestamp(new Date())
                .headers(headers)
                .build();
    }
}
