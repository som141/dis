package discordgateway.stocknode.application;

import discordgateway.stock.command.StockCommand;
import discordgateway.stock.command.StockCommandEnvelope;
import discordgateway.stock.event.StockCommandResultEvent;
import discordgateway.stocknode.bootstrap.StockQuoteProperties;
import discordgateway.stocknode.quote.service.QuoteService;
import discordgateway.stocknode.quote.service.QuoteUsage;

import java.time.Clock;

public class StockCommandApplicationService {

    private final QuoteService quoteService;
    private final TradeExecutionService tradeExecutionService;
    private final BalanceQueryService balanceQueryService;
    private final PortfolioQueryService portfolioQueryService;
    private final TradeHistoryQueryService tradeHistoryQueryService;
    private final StockResponseFormatter stockResponseFormatter;
    private final StockQuoteProperties stockQuoteProperties;
    private final Clock clock;
    private final String producerNode;

    public StockCommandApplicationService(
            QuoteService quoteService,
            TradeExecutionService tradeExecutionService,
            BalanceQueryService balanceQueryService,
            PortfolioQueryService portfolioQueryService,
            TradeHistoryQueryService tradeHistoryQueryService,
            StockResponseFormatter stockResponseFormatter,
            StockQuoteProperties stockQuoteProperties,
            Clock clock,
            String producerNode
    ) {
        this.quoteService = quoteService;
        this.tradeExecutionService = tradeExecutionService;
        this.balanceQueryService = balanceQueryService;
        this.portfolioQueryService = portfolioQueryService;
        this.tradeHistoryQueryService = tradeHistoryQueryService;
        this.stockResponseFormatter = stockResponseFormatter;
        this.stockQuoteProperties = stockQuoteProperties;
        this.clock = clock;
        this.producerNode = producerNode;
    }

    public StockCommandResultEvent handle(StockCommandEnvelope envelope) {
        StockCommand command = envelope.command();
        return switch (command) {
            case StockCommand.Quote quote -> success(
                    envelope,
                    stockResponseFormatter.formatQuote(
                            stockQuoteProperties.getDefaultMarket(),
                            quote.symbol(),
                            quoteService.getQuote(
                                    stockQuoteProperties.getDefaultMarket(),
                                    quote.symbol(),
                                    QuoteUsage.QUERY
                            )
                    ),
                    "QUOTE"
            );
            case StockCommand.Buy buy -> success(
                    envelope,
                    stockResponseFormatter.formatTrade(
                            tradeExecutionService.buy(
                                    buy.guildId(),
                                    buy.requesterId(),
                                    buy.symbol(),
                                    buy.amount()
                            )
                    ),
                    "BUY"
            );
            case StockCommand.Sell sell -> success(
                    envelope,
                    stockResponseFormatter.formatTrade(
                            tradeExecutionService.sell(
                                    sell.guildId(),
                                    sell.requesterId(),
                                    sell.symbol(),
                                    sell.quantity()
                            )
                    ),
                    "SELL"
            );
            case StockCommand.Balance balance -> success(
                    envelope,
                    stockResponseFormatter.formatBalance(
                            balanceQueryService.getBalance(balance.guildId(), balance.requesterId())
                    ),
                    "BALANCE"
            );
            case StockCommand.Portfolio portfolio -> success(
                    envelope,
                    stockResponseFormatter.formatPortfolio(
                            portfolioQueryService.getPortfolio(portfolio.guildId(), portfolio.requesterId())
                    ),
                    "PORTFOLIO"
            );
            case StockCommand.History history -> success(
                    envelope,
                    stockResponseFormatter.formatHistory(
                            tradeHistoryQueryService.getHistory(
                                    history.guildId(),
                                    history.requesterId(),
                                    history.limit()
                            )
                    ),
                    "HISTORY"
            );
            case StockCommand.Rank rank -> failure(
                    envelope,
                    stockResponseFormatter.formatNotImplemented("rank(" + rank.period() + ")"),
                    "NOT_IMPLEMENTED"
            );
        };
    }

    public StockCommandResultEvent failure(StockCommandEnvelope envelope, Throwable throwable) {
        return failure(
                envelope,
                stockResponseFormatter.formatFailure(throwable),
                "FAILED"
        );
    }

    private StockCommandResultEvent success(StockCommandEnvelope envelope, String message, String resultType) {
        return buildEvent(envelope, true, message, resultType);
    }

    private StockCommandResultEvent failure(StockCommandEnvelope envelope, String message, String resultType) {
        return buildEvent(envelope, false, message, resultType);
    }

    private StockCommandResultEvent buildEvent(
            StockCommandEnvelope envelope,
            boolean success,
            String message,
            String resultType
    ) {
        return new StockCommandResultEvent(
                envelope.commandId(),
                envelope.schemaVersion(),
                clock.millis(),
                producerNode,
                envelope.responseTargetNode(),
                envelope.command().guildId(),
                envelope.command().requesterId(),
                success,
                message,
                resultType
        );
    }
}
