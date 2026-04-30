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
                .add("시세 조회")
                .add("- 시장: " + market)
                .add("- 종목: " + symbol)
                .add("- 가격: " + formatMoney(result.quote().price()))
                .add("- 시각: " + TIME_FORMATTER.format(result.quote().quotedAt().atOffset(ZoneOffset.UTC)))
                .add("- 상태: " + freshnessLabel(result.fresh()) + " / " + sourceLabel(result.source()))
                .toString();
    }

    public String formatTrade(TradeExecutionResult result) {
        StringJoiner joiner = new StringJoiner(System.lineSeparator())
                .add(result.side() == TradeSide.BUY ? "매수 완료" : "매도 완료")
                .add("- 시장: " + result.market())
                .add("- 종목: " + result.symbol())
                .add("- 체결가: " + formatMoney(result.unitPrice()))
                .add("- 체결수량: " + formatQuantity(result.executedQuantity()))
                .add("- 체결금액: " + formatMoney(result.settledAmount()))
                .add("- 남은 현금: " + formatMoney(result.remainingCash()))
                .add("- 남은 보유수량: " + formatQuantity(result.remainingPositionQuantity()));
        if (result.side() == TradeSide.BUY && result.requestedAmount() != null) {
            joiner.add("- 요청 금액: " + formatMoney(result.requestedAmount()));
        }
        if (result.side() == TradeSide.SELL && result.requestedQuantity() != null) {
            joiner.add("- 요청 수량: " + formatQuantity(result.requestedQuantity()));
        }
        return joiner.toString();
    }

    public String formatBalance(BalanceView balanceView) {
        return new StringJoiner(System.lineSeparator())
                .add("잔고 조회")
                .add("- 계좌 ID: " + balanceView.accountId())
                .add("- 현금: " + formatMoney(balanceView.cashBalance()))
                .toString();
    }

    public String formatPortfolio(PortfolioView portfolioView) {
        StringJoiner joiner = new StringJoiner(System.lineSeparator())
                .add("포트폴리오 조회")
                .add("- 현금: " + formatMoney(portfolioView.cashBalance()))
                .add("- 평가금액: " + formatMoney(portfolioView.totalMarketValue()))
                .add("- 총자산: " + formatMoney(portfolioView.totalEquity()))
                .add("- 평가손익: " + formatMoney(portfolioView.totalProfitLoss()));
        if (portfolioView.positions().isEmpty()) {
            return joiner.add("- 보유 종목이 없습니다.").toString();
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
                .add("거래내역 조회")
                .add("- 계좌 ID: " + tradeHistoryView.accountId())
                .add("- 현금: " + formatMoney(tradeHistoryView.cashBalance()));
        if (tradeHistoryView.entries().isEmpty()) {
            return joiner.add("- 거래 내역이 없습니다.").toString();
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

    public String formatNotImplemented(String commandName) {
        return "아직 구현되지 않은 stock 명령입니다: " + commandName;
    }

    public String formatFailure(Throwable throwable) {
        if (throwable == null || throwable.getMessage() == null || throwable.getMessage().isBlank()) {
            return "stock 명령 처리 중 알 수 없는 오류가 발생했습니다.";
        }
        return "stock 명령 처리 중 오류가 발생했습니다: " + throwable.getMessage();
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
}
