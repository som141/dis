package discordgateway.stocknode.application;

import discordgateway.stock.command.StockCommand;
import discordgateway.stock.command.StockCommandEnvelope;
import discordgateway.stock.event.StockCommandResultEvent;
import discordgateway.stocknode.bootstrap.StockQuoteProperties;
import discordgateway.stocknode.quote.service.StockQuoteResult;
import discordgateway.stocknode.quote.service.QuoteService;
import discordgateway.stocknode.quote.service.QuoteUsage;

import java.time.Clock;
import java.util.List;

public class StockCommandApplicationService {

    private final QuoteService quoteService;
    private final TradeExecutionService tradeExecutionService;
    private final BalanceQueryService balanceQueryService;
    private final PortfolioQueryService portfolioQueryService;
    private final TradeHistoryQueryService tradeHistoryQueryService;
    private final StockListQueryService stockListQueryService;
    private final StockWatchlistService stockWatchlistService;
    private final RankingService rankingService;
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
            StockListQueryService stockListQueryService,
            StockWatchlistService stockWatchlistService,
            RankingService rankingService,
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
        this.stockListQueryService = stockListQueryService;
        this.stockWatchlistService = stockWatchlistService;
        this.rankingService = rankingService;
        this.stockResponseFormatter = stockResponseFormatter;
        this.stockQuoteProperties = stockQuoteProperties;
        this.clock = clock;
        this.producerNode = producerNode;
    }

    public StockCommandResultEvent handle(StockCommandEnvelope envelope) {
        StockCommand command = envelope.command();
        return switch (command) {
            case StockCommand.Quote quote -> {
                quote.symbols().forEach(symbol ->
                        stockWatchlistService.validateTradable(stockQuoteProperties.getDefaultMarket(), symbol)
                );
                List<StockQuoteResult> quotes = quote.symbols().stream()
                        .map(symbol -> quoteService.getQuote(
                                stockQuoteProperties.getDefaultMarket(),
                                symbol,
                                QuoteUsage.QUERY
                        ))
                        .toList();
                String message = quote.symbols().size() == 1
                        ? stockResponseFormatter.formatQuote(
                                stockQuoteProperties.getDefaultMarket(),
                                quote.symbols().getFirst(),
                                quotes.getFirst()
                        )
                        : stockResponseFormatter.formatQuoteTable(
                                stockQuoteProperties.getDefaultMarket(),
                                quote.symbols(),
                                quotes
                );
                yield success(envelope, message, "QUOTE");
            }
            case StockCommand.ListQuotes ignored -> success(
                    envelope,
                    stockResponseFormatter.formatWatchlist(stockListQueryService.getUsTopList()),
                    "LIST"
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
            case StockCommand.Rank rank -> success(
                    envelope,
                    stockResponseFormatter.formatRanking(
                            rankingService.getRanking(rank.guildId(), rank.period())
                    ),
                    "RANK"
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
