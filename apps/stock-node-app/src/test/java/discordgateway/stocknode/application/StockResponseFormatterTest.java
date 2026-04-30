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

        assertThat(message).contains("시세 조회");
        assertThat(message).contains("AAPL");
        assertThat(message).contains("provider_refresh");
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
                        true
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
                        Instant.parse("2026-04-30T01:00:00Z")
                ))
        ));

        assertThat(portfolio).contains("포트폴리오 조회");
        assertThat(portfolio).contains("AAPL");
        assertThat(history).contains("거래내역 조회");
        assertThat(history).contains("BUY AAPL");
    }
}
