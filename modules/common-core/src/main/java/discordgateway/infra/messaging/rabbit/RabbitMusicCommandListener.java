package discordgateway.infra.messaging.rabbit;

import discordgateway.common.bootstrap.AppProperties;
import discordgateway.common.bootstrap.MessagingProperties;
import discordgateway.common.command.CommandResult;
import discordgateway.common.command.MusicCommandEnvelope;
import discordgateway.common.command.MusicCommandMessage;
import discordgateway.common.command.MusicCommandResponseMode;
import discordgateway.common.command.MusicCommandResultEvent;
import discordgateway.playback.application.MusicWorkerService;
import discordgateway.playback.domain.CommandProcessingStatus;
import discordgateway.playback.domain.ProcessedCommand;
import discordgateway.playback.domain.ProcessedCommandRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;

import java.util.concurrent.CompletionException;

public class RabbitMusicCommandListener {

    private static final Logger log = LoggerFactory.getLogger(RabbitMusicCommandListener.class);

    private final MusicWorkerService musicWorkerService;
    private final ProcessedCommandRepository processedCommandRepository;
    private final MessagingProperties messagingProperties;
    private final RabbitMusicCommandResultPublisher resultPublisher;
    private final String producerNode;

    public RabbitMusicCommandListener(
            MusicWorkerService musicWorkerService,
            ProcessedCommandRepository processedCommandRepository,
            MessagingProperties messagingProperties,
            RabbitMusicCommandResultPublisher resultPublisher,
            AppProperties appProperties
    ) {
        this.musicWorkerService = musicWorkerService;
        this.processedCommandRepository = processedCommandRepository;
        this.messagingProperties = messagingProperties;
        this.resultPublisher = resultPublisher;
        this.producerNode = appProperties.getNodeName();
    }

    @RabbitListener(queues = "${messaging.command-queue:music.command.queue}")
    public void handle(MusicCommandEnvelope envelope) {
        MusicCommandMessage message = envelope.message();

        log.atInfo()
                .addKeyValue("commandId", message.commandId())
                .addKeyValue("schemaVersion", message.schemaVersion())
                .addKeyValue("producer", message.producer())
                .addKeyValue("commandType", message.command().getClass().getSimpleName())
                .addKeyValue("guildId", message.command().guildId())
                .log("music-command consume");

        ProcessedCommand existing = processedCommandRepository.find(message.commandId());
        if (existing != null) {
            publishResult(envelope, replayOrDuplicate(existing, message.commandId()), existingResultType(existing));
            return;
        }

        if (!processedCommandRepository.tryStart(message.commandId(), messagingProperties.getCommandDedupTtlMs())) {
            ProcessedCommand concurrent = processedCommandRepository.find(message.commandId());
            CommandResult result = concurrent != null
                    ? replayOrDuplicate(concurrent, message.commandId())
                    : duplicateInProgress(message.commandId());
            publishResult(envelope, result, "IN_PROGRESS");
            return;
        }

        try {
            CommandResult result = musicWorkerService.handle(message).join();
            processedCommandRepository.complete(
                    message.commandId(),
                    result,
                    messagingProperties.getCommandDedupTtlMs()
            );
            publishResult(envelope, result, "SUCCESS");
        } catch (CompletionException e) {
            processedCommandRepository.remove(message.commandId());
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            publishResult(envelope, failureResult(cause), "FAILED");
            throw reject(message.commandId(), cause);
        } catch (RuntimeException e) {
            processedCommandRepository.remove(message.commandId());
            publishResult(envelope, failureResult(e), "FAILED");
            throw reject(message.commandId(), e);
        }
    }

    private CommandResult replayOrDuplicate(ProcessedCommand existing, String commandId) {
        if (existing.status() == CommandProcessingStatus.COMPLETED && existing.result() != null) {
            log.atInfo()
                    .addKeyValue("commandId", commandId)
                    .addKeyValue("status", "COMPLETED")
                    .log("music-command duplicate replay");
            return existing.result();
        }

        log.atInfo()
                .addKeyValue("commandId", commandId)
                .addKeyValue("status", existing.status())
                .log("music-command duplicate ignored");
        return duplicateInProgress(commandId);
    }

    private String existingResultType(ProcessedCommand existing) {
        return existing.status() == CommandProcessingStatus.COMPLETED ? "DUPLICATE_REPLAY" : "IN_PROGRESS";
    }

    private CommandResult duplicateInProgress(String commandId) {
        return CommandResult.ephemeral("동일 명령이 이미 처리 중입니다. commandId=" + commandId);
    }

    private CommandResult failureResult(Throwable cause) {
        String message = cause != null && cause.getMessage() != null && !cause.getMessage().isBlank()
                ? cause.getMessage()
                : "알 수 없는 오류가 발생했습니다.";
        return CommandResult.ephemeral("명령 처리 중 오류가 발생했습니다: " + message);
    }

    private void publishResult(MusicCommandEnvelope envelope, CommandResult result, String resultType) {
        MusicCommandMessage message = envelope.message();
        resultPublisher.publish(new MusicCommandResultEvent(
                message.commandId(),
                message.schemaVersion(),
                System.currentTimeMillis(),
                producerNode,
                envelope.responseTargetNode(),
                message.command().guildId(),
                "SUCCESS".equals(resultType) || "DUPLICATE_REPLAY".equals(resultType),
                result.message(),
                envelope.responseMode() == MusicCommandResponseMode.EPHEMERAL || result.ephemeral(),
                resultType
        ));
    }

    private AmqpRejectAndDontRequeueException reject(String commandId, Throwable cause) {
        log.atWarn()
                .addKeyValue("commandId", commandId)
                .setCause(cause)
                .log("music-command failed sending to DLQ");
        return new AmqpRejectAndDontRequeueException(
                "Command processing failed. commandId=" + commandId,
                cause
        );
    }
}
