package discordgateway.stocknode.messaging;

import discordgateway.stocknode.application.StockCommandApplicationService;

// Future RabbitMQ consumer adapter for stock commands. Week-0 keeps it as scaffolding only.
public class StockCommandListener {

    private final StockCommandApplicationService stockCommandApplicationService;
    private final StockCommandResultPublisher stockCommandResultPublisher;

    public StockCommandListener(
            StockCommandApplicationService stockCommandApplicationService,
            StockCommandResultPublisher stockCommandResultPublisher
    ) {
        this.stockCommandApplicationService = stockCommandApplicationService;
        this.stockCommandResultPublisher = stockCommandResultPublisher;
    }

    public StockCommandApplicationService stockCommandApplicationService() {
        return stockCommandApplicationService;
    }

    public StockCommandResultPublisher stockCommandResultPublisher() {
        return stockCommandResultPublisher;
    }
}
