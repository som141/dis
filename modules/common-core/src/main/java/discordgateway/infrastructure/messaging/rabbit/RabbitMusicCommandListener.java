package discordgateway.infrastructure.messaging.rabbit;

import discordgateway.application.CommandResult;
import discordgateway.application.MusicCommandMessage;
import discordgateway.application.MusicWorkerService;
import discordgateway.bootstrap.MessagingProperties;
import discordgateway.domain.CommandProcessingStatus;
import discordgateway.domain.ProcessedCommand;
import discordgateway.domain.ProcessedCommandRepository;
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

    public RabbitMusicCommandListener(
            MusicWorkerService musicWorkerService,
            ProcessedCommandRepository processedCommandRepository,
            MessagingProperties messagingProperties
    ) {
        this.musicWorkerService = musicWorkerService;
        this.processedCommandRepository = processedCommandRepository;
        this.messagingProperties = messagingProperties;
    }

    @RabbitListener(queues = "${messaging.command-queue:music.command.queue}")
    public CommandResult handle(MusicCommandMessage message) {
        log.atInfo()
                .addKeyValue("commandId", message.commandId())
                .addKeyValue("schemaVersion", message.schemaVersion())
                .addKeyValue("producer", message.producer())
                .addKeyValue("commandType", message.command().getClass().getSimpleName())
                .addKeyValue("guildId", message.command().guildId())
                .log("music-command rpc");

        ProcessedCommand existing = processedCommandRepository.find(message.commandId());
        if (existing != null) {
            return replayOrDuplicate(existing, message.commandId());
        }

        if (!processedCommandRepository.tryStart(message.commandId(), messagingProperties.getCommandDedupTtlMs())) {
            ProcessedCommand concurrent = processedCommandRepository.find(message.commandId());
            if (concurrent != null) {
                return replayOrDuplicate(concurrent, message.commandId());
            }
            return duplicateInProgress(message.commandId());
        }

        try {
            CommandResult result = musicWorkerService.handle(message).join();
            processedCommandRepository.complete(
                    message.commandId(),
                    result,
                    messagingProperties.getCommandDedupTtlMs()
            );
            return result;
        } catch (CompletionException e) {
            processedCommandRepository.remove(message.commandId());
            throw reject(message.commandId(), e.getCause() != null ? e.getCause() : e);
        } catch (RuntimeException e) {
            processedCommandRepository.remove(message.commandId());
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

    private CommandResult duplicateInProgress(String commandId) {
        return CommandResult.ephemeral("동일 명령이 이미 처리 중입니다. commandId=" + commandId);
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
