package discordgateway.stocknode.application;

import discordgateway.stocknode.quote.service.StockQuoteResult;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;

public class StockResponseFormatter {

    private static final ZoneId DISPLAY_ZONE = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'KST'").withZone(DISPLAY_ZONE);
    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(DISPLAY_ZONE);

    public String formatQuote(String market, String symbol, StockQuoteResult result) {
        StringJoiner joiner = new StringJoiner(System.lineSeparator())
                .add("**주식 시세 조회 결과**")
                .add("- 시장: " + displayMarketName(market))
                .add("- 종목: " + symbol.toUpperCase(Locale.ROOT))
                .add("- 현재가: " + formatMoney(result.quote().price()))
                .add("- 마지막 갱신: " + DATE_TIME_FORMATTER.format(result.quote().quotedAt()))
                .add("- 상태: " + freshnessDescription(result.fresh()));
        if (!result.fresh()) {
            joiner.add("- 안내: 이 시세는 최신 갱신 기준보다 오래되었을 수 있습니다.");
        }
        return joiner.toString();
    }

    public String formatQuoteTable(String market, List<String> symbols, List<StockQuoteResult> results) {
        boolean hasStale = results.stream().anyMatch(result -> !result.fresh());
        StringBuilder builder = new StringBuilder()
                .append("**주식 시세표**").append(System.lineSeparator())
                .append("- 시장: ").append(displayMarketName(market)).append(System.lineSeparator())
                .append("```text").append(System.lineSeparator())
                .append(String.format(
                        Locale.ROOT,
                        "%-8s %-14s %-8s %-10s%n",
                        "SYMBOL",
                        "PRICE",
                        "STATUS",
                        "TIME"
                ));

        for (int index = 0; index < symbols.size(); index++) {
            StockQuoteResult result = results.get(index);
            builder.append(String.format(
                    Locale.ROOT,
                    "%-8s %-14s %-8s %-10s%n",
                    symbols.get(index),
                    formatMoney(result.quote().price()),
                    freshnessShortLabel(result.fresh()),
                    TIME_FORMATTER.format(result.quote().quotedAt())
            ));
        }

        builder.append("```");
        if (hasStale) {
            builder.append(System.lineSeparator())
                    .append("- 안내: 일부 종목은 최신 갱신 기준보다 오래된 시세입니다.");
        }
        return builder.toString();
    }

    public String formatWatchlist(StockListView stockListView) {
        StringJoiner joiner = new StringJoiner(System.lineSeparator())
                .add("**미국 시가총액 상위 10개 종목**")
                .add("```text");

        for (StockListItemView item : stockListView.items()) {
            if (!item.quoteReady()) {
                joiner.add(String.format(
                        Locale.ROOT,
                        "%2d. %-34s (%-6s) - 시세 준비 중",
                        item.rankNo(),
                        item.name(),
                        item.symbol()
                ));
                continue;
            }

            String staleSuffix = item.fresh() ? "" : "  [지연]";
            joiner.add(String.format(
                    Locale.ROOT,
                    "%2d. %-34s (%-6s) - %s / %s%s",
                    item.rankNo(),
                    item.name(),
                    item.symbol(),
                    formatMoney(item.price()),
                    formatSignedPercent(item.changeRate()),
                    staleSuffix
            ));
        }

        return joiner
                .add("```")
                .add("- 데이터 출처: Finnhub REST API")
                .add("- 갱신 주기: 20초")
                .add("- 안내: [지연] 표시는 최신 갱신 기준보다 오래된 시세입니다.")
                .toString();
    }

    public String formatTrade(TradeExecutionResult result) {
        StringJoiner joiner = new StringJoiner(System.lineSeparator())
                .add(result.side() == TradeSide.BUY ? "**매수 체결 완료**" : "**매도 체결 완료**")
                .add("- 주문자: <@" + result.userId() + ">")
                .add("- 시장: " + displayMarketName(result.market()))
                .add("- 종목: " + result.symbol())
                .add("- 적용 레버리지: " + result.leverage() + "배")
                .add("- 체결 단가: " + formatMoney(result.unitPrice()))
                .add("- 증거금: " + formatMoney(result.marginAmount()))
                .add("- 포지션 규모: " + formatMoney(result.notionalAmount()))
                .add("- 체결 수량: " + formatQuantity(result.executedQuantity()) + "주")
                .add("- 정산 금액: " + formatMoney(result.settledAmount()))
                .add("- 남은 현금: " + formatMoney(result.remainingCash()))
                .add("- 현재 보유 수량: " + formatQuantity(result.remainingPositionQuantity()) + "주");
        if (result.requestedQuantity() != null) {
            joiner.add("- 요청 수량: " + formatQuantity(result.requestedQuantity()) + "주");
        }
        if (result.warningMessage() != null && !result.warningMessage().isBlank()) {
            joiner.add("- 주의: " + localizeWarning(result.warningMessage()));
        }
        return joiner.toString();
    }

    public String formatBalance(BalanceView balanceView) {
        return new StringJoiner(System.lineSeparator())
                .add("**이번 시즌 보유 현금**")
                .add("- 계좌 ID: " + balanceView.accountId())
                .add("- 보유 현금: " + formatMoney(balanceView.cashBalance()))
                .toString();
    }

    public String formatPortfolio(PortfolioView portfolioView) {
        StringJoiner joiner = new StringJoiner(System.lineSeparator())
                .add("**현재 포트폴리오**")
                .add("- 현금: " + formatMoney(portfolioView.cashBalance()))
                .add("- 보유 종목 평가금액: " + formatMoney(portfolioView.totalMarketValue()))
                .add("- 총 평가 자산: " + formatMoney(portfolioView.totalEquity()))
                .add("- 총 손익: " + formatMoney(portfolioView.totalProfitLoss()));
        if (portfolioView.positions().isEmpty()) {
            return joiner.add("- 보유 중인 종목이 없습니다.").toString();
        }

        for (PortfolioPositionView position : portfolioView.positions()) {
            joiner.add(String.format(
                    Locale.ROOT,
                    "- %s · %d배 · 수량 %s주 · 평균단가 %s · 현재가 %s · 증거금 %s · 포지션 규모 %s · 손익 %s · %s",
                    position.symbol(),
                    position.leverage(),
                    formatQuantity(position.quantity()),
                    formatMoney(position.averageCost()),
                    formatMoney(position.currentPrice()),
                    formatMoney(position.marginAmount()),
                    formatMoney(position.notionalAmount()),
                    formatMoney(position.profitLoss()),
                    freshnessDescription(position.fresh())
            ));
        }
        return joiner.toString();
    }

    public String formatHistory(TradeHistoryView tradeHistoryView) {
        StringJoiner joiner = new StringJoiner(System.lineSeparator())
                .add("**최근 거래 내역**")
                .add("- 계좌 ID: " + tradeHistoryView.accountId())
                .add("- 현재 현금: " + formatMoney(tradeHistoryView.cashBalance()));
        if (tradeHistoryView.entries().isEmpty()) {
            return joiner.add("- 아직 거래 내역이 없습니다.").toString();
        }

        for (TradeHistoryEntryView entry : tradeHistoryView.entries()) {
            joiner.add(String.format(
                    Locale.ROOT,
                    "- %s · %s · %d배 · 수량 %s주 · 단가 %s · 증거금 %s · 포지션 규모 %s · 시각 %s",
                    tradeSideLabel(entry.side()),
                    entry.symbol(),
                    entry.leverage(),
                    formatQuantity(entry.quantity()),
                    formatMoney(entry.unitPrice()),
                    formatMoney(entry.marginAmount()),
                    formatMoney(entry.notionalAmount()),
                    DATE_TIME_FORMATTER.format(entry.occurredAt())
            ));
        }
        return joiner.toString();
    }

    public String formatRanking(RankingView rankingView) {
        StringJoiner joiner = new StringJoiner(System.lineSeparator())
                .add("**서버 수익률 순위**")
                .add("- 시즌: " + rankingView.seasonKey())
                .add("- 집계 기준: " + periodLabel(rankingView.period()))
                .add("- 집계 시각: " + DATE_TIME_FORMATTER.format(rankingView.generatedAt()));
        if (rankingView.entries().isEmpty()) {
            return joiner.add("- 아직 순위를 계산할 참가자가 없습니다.").toString();
        }

        int index = 1;
        for (RankingEntryView entry : rankingView.entries()) {
            joiner.add(String.format(
                    Locale.ROOT,
                    "- %d위 · <@%d> · 수익률 %s%% · 평가 자산 %s · 기준 자산 %s",
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
        return "아직 구현되지 않은 주식 명령입니다: " + commandName;
    }

    public String formatFailure(Throwable throwable) {
        if (throwable instanceof QuoteNotReadyException || throwable instanceof StaleQuoteException) {
            return "현재 45초 이내 최신 시세가 없어 거래하거나 조회할 수 없습니다. 잠시 후 다시 시도해 주세요.";
        }
        if (throwable instanceof InsufficientCashException) {
            return "보유 현금이 부족해 주문을 처리할 수 없습니다.";
        }
        if (throwable instanceof InsufficientQuantityException) {
            return "보유 수량이 부족해 매도할 수 없습니다.";
        }
        if (throwable instanceof InvalidLeverageException) {
            return "레버리지는 1배부터 50배 사이에서만 입력할 수 있습니다.";
        }
        if (throwable instanceof LeverageMismatchException) {
            return "같은 종목에는 이미 다른 레버리지 포지션이 있어 합산할 수 없습니다.";
        }
        if (throwable instanceof SymbolNotTradableException) {
            return "현재 지원하지 않는 종목입니다. 미국 시가총액 상위 10개 종목만 거래할 수 있습니다.";
        }
        if (throwable instanceof InvalidTradeArgumentException) {
            return throwable.getMessage();
        }
        if (throwable == null || throwable.getMessage() == null || throwable.getMessage().isBlank()) {
            return "주식 명령 처리 중 알 수 없는 오류가 발생했습니다.";
        }
        return "주식 명령 처리에 실패했습니다: " + throwable.getMessage();
    }

    private String tradeSideLabel(TradeSide tradeSide) {
        return switch (tradeSide) {
            case BUY -> "매수";
            case SELL -> "매도";
        };
    }

    private String periodLabel(String period) {
        return switch (period.toLowerCase(Locale.ROOT)) {
            case "day" -> "일간";
            case "week" -> "주간";
            case "all" -> "시즌 누적";
            default -> period;
        };
    }

    private String displayMarketName(String market) {
        return "us".equalsIgnoreCase(market) ? "미국" : market.toUpperCase(Locale.ROOT);
    }

    private String freshnessDescription(boolean fresh) {
        return fresh ? "최신 시세" : "지연 시세";
    }

    private String freshnessShortLabel(boolean fresh) {
        return fresh ? "FRESH" : "STALE";
    }

    private String localizeWarning(String warningMessage) {
        if (warningMessage.contains("50x leverage")) {
            return "50배 레버리지는 약 2%만 불리하게 움직여도 포지션 가치가 크게 훼손될 수 있습니다.";
        }
        return warningMessage;
    }

    private String formatMoney(BigDecimal value) {
        return decimalFormat("#,##0.0000").format(value.setScale(4, RoundingMode.HALF_UP));
    }

    private String formatQuantity(BigDecimal value) {
        return decimalFormat("#,##0.00000000").format(value.setScale(8, RoundingMode.HALF_UP));
    }

    private String formatPercent(BigDecimal value) {
        return decimalFormat("#,##0.0000").format(value.setScale(4, RoundingMode.HALF_UP));
    }

    private String formatSignedPercent(BigDecimal value) {
        if (value == null) {
            return "변동률 없음";
        }
        BigDecimal normalized = value.setScale(2, RoundingMode.HALF_UP);
        String prefix = normalized.signum() > 0 ? "+" : "";
        return prefix + decimalFormat("#,##0.00").format(normalized) + "%";
    }

    private DecimalFormat decimalFormat(String pattern) {
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.US);
        DecimalFormat decimalFormat = new DecimalFormat(pattern, symbols);
        decimalFormat.setGroupingUsed(true);
        decimalFormat.setParseBigDecimal(true);
        return decimalFormat;
    }
}
