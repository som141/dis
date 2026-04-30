package discordgateway.stocknode.application;

import discordgateway.stocknode.quote.service.QuoteSource;
import discordgateway.stocknode.quote.service.StockQuoteResult;

import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.StringJoiner;

public class StockResponseFormatter {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    public String formatQuote(String market, String symbol, StockQuoteResult result) {
        return new StringJoiner(System.lineSeparator())
                .add("stock quote")
                .add("- market: " + market)
                .add("- symbol: " + symbol)
                .add("- price: " + formatMoney(result.quote().price()))
                .add("- quotedAt: " + TIME_FORMATTER.format(result.quote().quotedAt().atOffset(ZoneOffset.UTC)))
                .add("- status: " + freshnessLabel(result.fresh()) + " / " + sourceLabel(result.source()))
                .toString();
    }

    public String formatTrade(TradeExecutionResult result) {
        StringJoiner joiner = new StringJoiner(System.lineSeparator())
                .add(result.side() == TradeSide.BUY ? "buy completed" : "sell completed")
                .add("- market: " + result.market())
                .add("- symbol: " + result.symbol())
                .add("- unitPrice: " + formatMoney(result.unitPrice()))
                .add("- executedQuantity: " + formatQuantity(result.executedQuantity()))
                .add("- settledAmount: " + formatMoney(result.settledAmount()))
                .add("- remainingCash: " + formatMoney(result.remainingCash()))
                .add("- remainingPositionQuantity: " + formatQuantity(result.remainingPositionQuantity()));
        if (result.side() == TradeSide.BUY && result.requestedAmount() != null) {
            joiner.add("- requestedAmount: " + formatMoney(result.requestedAmount()));
        }
        if (result.side() == TradeSide.SELL && result.requestedQuantity() != null) {
            joiner.add("- requestedQuantity: " + formatQuantity(result.requestedQuantity()));
        }
        return joiner.toString();
    }

    public String formatBalance(BalanceView balanceView) {
        return new StringJoiner(System.lineSeparator())
                .add("stock balance")
                .add("- accountId: " + balanceView.accountId())
                .add("- cash: " + formatMoney(balanceView.cashBalance()))
                .toString();
    }

    public String formatPortfolio(PortfolioView portfolioView) {
        StringJoiner joiner = new StringJoiner(System.lineSeparator())
                .add("stock portfolio")
                .add("- cash: " + formatMoney(portfolioView.cashBalance()))
                .add("- marketValue: " + formatMoney(portfolioView.totalMarketValue()))
                .add("- totalEquity: " + formatMoney(portfolioView.totalEquity()))
                .add("- profitLoss: " + formatMoney(portfolioView.totalProfitLoss()));
        if (portfolioView.positions().isEmpty()) {
            return joiner.add("- no holdings").toString();
        }

        for (PortfolioPositionView position : portfolioView.positions()) {
            joiner.add(String.format(
                    Locale.ROOT,
                    "- %s qty=%s avg=%s now=%s pnl=%s %s",
                    position.symbol(),
                    formatQuantity(position.quantity()),
                    formatMoney(position.averageCost()),
                    formatMoney(position.currentPrice()),
                    formatMoney(position.profitLoss()),
                    freshnessLabel(position.fresh())
            ));
        }
        return joiner.toString();
    }

    public String formatHistory(TradeHistoryView tradeHistoryView) {
        StringJoiner joiner = new StringJoiner(System.lineSeparator())
                .add("stock history")
                .add("- accountId: " + tradeHistoryView.accountId())
                .add("- cash: " + formatMoney(tradeHistoryView.cashBalance()));
        if (tradeHistoryView.entries().isEmpty()) {
            return joiner.add("- no trade history").toString();
        }

        for (TradeHistoryEntryView entry : tradeHistoryView.entries()) {
            joiner.add(String.format(
                    Locale.ROOT,
                    "- %s %s qty=%s price=%s at=%s",
                    entry.side().name(),
                    entry.symbol(),
                    formatQuantity(entry.quantity()),
                    formatMoney(entry.unitPrice()),
                    TIME_FORMATTER.format(entry.occurredAt().atOffset(ZoneOffset.UTC))
            ));
        }
        return joiner.toString();
    }

    public String formatRanking(RankingView rankingView) {
        StringJoiner joiner = new StringJoiner(System.lineSeparator())
                .add("stock ranking")
                .add("- period: " + rankingView.period())
                .add("- generatedAt: " + TIME_FORMATTER.format(rankingView.generatedAt().atOffset(ZoneOffset.UTC)));
        if (rankingView.entries().isEmpty()) {
            return joiner.add("- no ranked accounts").toString();
        }

        int index = 1;
        for (RankingEntryView entry : rankingView.entries()) {
            joiner.add(String.format(
                    Locale.ROOT,
                    "- #%d user=%d return=%s%% equity=%s baseline=%s",
                    index++,
                    entry.userId(),
                    formatPercent(entry.returnRatePercent()),
                    formatMoney(entry.totalEquity()),
                    formatMoney(entry.baselineEquity())
            ));
        }
        return joiner.toString();
    }

    public String formatNotImplemented(String commandName) {
        return "stock command is not implemented yet: " + commandName;
    }

    public String formatFailure(Throwable throwable) {
        if (throwable == null || throwable.getMessage() == null || throwable.getMessage().isBlank()) {
            return "stock command failed with an unknown error.";
        }
        return "stock command failed: " + throwable.getMessage();
    }

    private String freshnessLabel(boolean fresh) {
        return fresh ? "fresh" : "stale";
    }

    private String sourceLabel(QuoteSource quoteSource) {
        return quoteSource.name().toLowerCase(Locale.ROOT);
    }

    private String formatMoney(BigDecimal value) {
        return value.setScale(4, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private String formatQuantity(BigDecimal value) {
        return value.setScale(8, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private String formatPercent(BigDecimal value) {
        return value.setScale(4, java.math.RoundingMode.HALF_UP).toPlainString();
    }
}
