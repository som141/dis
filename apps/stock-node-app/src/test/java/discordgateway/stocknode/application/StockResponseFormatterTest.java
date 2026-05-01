package discordgateway.stocknode.application;

import discordgateway.stocknode.quote.model.StockQuote;
import discordgateway.stocknode.quote.service.QuoteSource;
import discordgateway.stocknode.quote.service.StockQuoteResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StockResponseFormatterTest {

    private final StockResponseFormatter formatter = new StockResponseFormatter();

    @Test
    void formatsQuoteResponse() {
        String message = formatter.formatQuote(
                "us",
                "AAPL",
                new StockQuoteResult(
                        new StockQuote("us", "AAPL", new BigDecimal("123.45"), Instant.parse("2026-04-30T01:00:00Z")),
                        QuoteSource.PROVIDER_REFRESH,
                        true
                )
        );

        assertThat(message).contains("주식 시세 조회 결과");
        assertThat(message).contains("AAPL");
        assertThat(message).contains("최신 시세");
        assertThat(message).contains("123.45");
    }

    @Test
    void formatsQuoteTableResponse() {
        String message = formatter.formatQuoteTable(
                "us",
                List.of("AAPL", "MSFT"),
                List.of(
                        new StockQuoteResult(
                                new StockQuote("us", "AAPL", new BigDecimal("123.45"), Instant.parse("2026-04-30T01:00:00Z")),
                                QuoteSource.PROVIDER_REFRESH,
                                true
                        ),
                        new StockQuoteResult(
                                new StockQuote("us", "MSFT", new BigDecimal("456.78"), Instant.parse("2026-04-30T01:00:01Z")),
                                QuoteSource.CACHE_STALE,
                                false
                        )
                )
        );

        assertThat(message).contains("주식 시세 표");
        assertThat(message).contains("```text");
        assertThat(message).contains("AAPL");
        assertThat(message).contains("MSFT");
        assertThat(message).contains("STALE");
    }

    @Test
    void formatsWatchlistResponse() {
        String message = formatter.formatWatchlist(new StockListView(
                "US",
                List.of(
                        new StockListItemView(1, "US", "NVDA", "NVIDIA Corporation", new BigDecimal("216.61"), new BigDecimal("4.00"), true, true),
                        new StockListItemView(2, "US", "AAPL", "Apple Inc.", null, null, false, false),
                        new StockListItemView(3, "US", "MSFT", "Microsoft Corporation", new BigDecimal("300.00"), new BigDecimal("-1.25"), true, false)
                )
        ));

        assertThat(message).contains("미국 시가총액 상위 10개 종목");
        assertThat(message).contains("NVIDIA Corporation");
        assertThat(message).contains("시세 준비 중");
        assertThat(message).contains("[지연]");
        assertThat(message).contains("Finnhub REST API");
    }

    @Test
    void formatsTradeResponseWithSimplifiedFields() {
        String message = formatter.formatTrade(new TradeExecutionResult(
                1L,
                1001L,
                2002L,
                TradeSide.BUY,
                "us",
                "NVDA",
                new BigDecimal("3.00000000"),
                10,
                new BigDecimal("59.8710"),
                new BigDecimal("598.7100"),
                new BigDecimal("3.00000000"),
                new BigDecimal("199.5700"),
                new BigDecimal("59.8710"),
                new BigDecimal("9940.1290"),
                new BigDecimal("3.00000000"),
                new BigDecimal("199.5700"),
                "50x leverage means that even an adverse move of around 2% may almost fully wipe the position value."
        ));

        assertThat(message).contains("매수 체결 완료");
        assertThat(message).contains("주문자");
        assertThat(message).contains("NVDA");
        assertThat(message).contains("10배");
        assertThat(message).contains("199.57");
        assertThat(message).contains("3주");
        assertThat(message).contains("59.87");
        assertThat(message).doesNotContain("시장");
        assertThat(message).doesNotContain("증거금");
        assertThat(message).doesNotContain("포지션 규모");
        assertThat(message).doesNotContain("남은 현금");
        assertThat(message).doesNotContain("현재 보유 수량");
    }

    @Test
    void formatsPortfolioAndHistoryResponses() {
        String portfolio = formatter.formatPortfolio(new PortfolioView(
                1L,
                1001L,
                2002L,
                new BigDecimal("9000.0000"),
                new BigDecimal("1000.0000"),
                new BigDecimal("800.0000"),
                new BigDecimal("10000.0000"),
                new BigDecimal("200.0000"),
                List.of(new PortfolioPositionView(
                        "AAPL",
                        new BigDecimal("5.00000000"),
                        new BigDecimal("160.0000"),
                        new BigDecimal("200.0000"),
                        new BigDecimal("1000.0000"),
                        new BigDecimal("800.0000"),
                        new BigDecimal("200.0000"),
                        true,
                        5,
                        new BigDecimal("800.0000"),
                        new BigDecimal("4000.0000")
                ))
        ));
        String history = formatter.formatHistory(new TradeHistoryView(
                1L,
                1001L,
                2002L,
                new BigDecimal("9000.0000"),
                List.of(new TradeHistoryEntryView(
                        "AAPL",
                        TradeSide.BUY,
                        new BigDecimal("5.00000000"),
                        new BigDecimal("200.0000"),
                        5,
                        new BigDecimal("1000.0000"),
                        new BigDecimal("5000.0000"),
                        Instant.parse("2026-04-30T01:00:00Z")
                ))
        ));

        assertThat(portfolio).contains("현재 포트폴리오");
        assertThat(portfolio).contains("AAPL");
        assertThat(portfolio).contains("5주");
        assertThat(history).contains("최근 거래 내역");
        assertThat(history).contains("매수");
        assertThat(history).contains("AAPL");
        assertThat(history).contains("200");
    }

    @Test
    void formatsRankingResponse() {
        String ranking = formatter.formatRanking(new RankingView(
                1001L,
                "2026-05",
                "day",
                Instant.parse("2026-04-30T01:00:00Z"),
                List.of(new RankingEntryView(
                        1L,
                        2002L,
                        new BigDecimal("10100.0000"),
                        new BigDecimal("10000.0000"),
                        new BigDecimal("1.0000")
                ))
        ));

        assertThat(ranking).contains("시즌 수익률 순위");
        assertThat(ranking).contains("<@2002>");
        assertThat(ranking).contains("1.0000%");
    }
}
