package discordgateway.stocknode.messaging;

import discordgateway.stock.command.StockCommandEnvelope;
import discordgateway.stocknode.application.StockCommandApplicationService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StockCommandListener {

    private static final Logger log = LoggerFactory.getLogger(StockCommandListener.class);

    private final StockCommandApplicationService stockCommandApplicationService;
    private final StockCommandResultPublisher stockCommandResultPublisher;

    public StockCommandListener(
            StockCommandApplicationService stockCommandApplicationService,
            StockCommandResultPublisher stockCommandResultPublisher
    ) {
        this.stockCommandApplicationService = stockCommandApplicationService;
        this.stockCommandResultPublisher = stockCommandResultPublisher;
    }

    @RabbitListener(queues = "${stock.messaging.command-queue:stock.command.queue}")
    public void handle(StockCommandEnvelope envelope) {
        try {
            stockCommandResultPublisher.publish(stockCommandApplicationService.handle(envelope));
        } catch (RuntimeException exception) {
            log.atWarn()
                    .addKeyValue("commandId", envelope.commandId())
                    .addKeyValue("commandType", envelope.command().getClass().getSimpleName())
                    .setCause(exception)
                    .log("stock-command failed");
            stockCommandResultPublisher.publish(stockCommandApplicationService.failure(envelope, exception));
        }
    }
}
