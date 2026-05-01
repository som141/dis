package discordgateway.stocknode.integration;

import discordgateway.stocknode.application.BalanceQueryService;
import discordgateway.stocknode.application.BalanceView;
import discordgateway.stocknode.application.AutoLiquidationService;
import discordgateway.stocknode.application.PortfolioQueryService;
import discordgateway.stocknode.application.PortfolioView;
import discordgateway.stocknode.application.TradeExecutionResult;
import discordgateway.stocknode.application.TradeExecutionService;
import discordgateway.stocknode.application.TradeHistoryQueryService;
import discordgateway.stocknode.application.TradeHistoryView;
import discordgateway.stocknode.cache.QuoteRepository;
import discordgateway.stocknode.quote.model.StockQuote;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class StockTradingIntegrationTest extends StockNodeIntegrationTestSupport {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private BalanceQueryService balanceQueryService;

    @Autowired
    private PortfolioQueryService portfolioQueryService;

    @Autowired
    private TradeExecutionService tradeExecutionService;

    @Autowired
    private AutoLiquidationService autoLiquidationService;

    @Autowired
    private TradeHistoryQueryService tradeHistoryQueryService;

    @Autowired
    private QuoteRepository quoteRepository;

    @BeforeEach
    void cleanTables() {
        jdbcTemplate.execute("TRUNCATE TABLE account_snapshot, allowance_ledger, trade_ledger, stock_position, stock_account RESTART IDENTITY CASCADE");
        quoteRepository.save(new StockQuote("US", "AAPL", new BigDecimal("200.00"), Instant.now()), Duration.ofSeconds(60));
    }

    @Test
    void runsAllowanceBuySellAndHistoryFlowAgainstRealPostgresAndRedis() {
        BalanceView balanceView = balanceQueryService.getBalance(1001L, 2002L);
        assertThat(balanceView.cashBalance()).isEqualByComparingTo("10000.0000");

        TradeExecutionResult buyResult = tradeExecutionService.buy(1001L, 2002L, "AAPL", new BigDecimal("5"), 1);
        assertThat(buyResult.side().name()).isEqualTo("BUY");
        assertThat(buyResult.executedQuantity()).isEqualByComparingTo("5.00000000");
        assertThat(buyResult.remainingCash()).isLessThan(new BigDecimal("10000.0000"));

        PortfolioView portfolioView = portfolioQueryService.getPortfolio(1001L, 2002L);
        assertThat(portfolioView.positions()).hasSize(1);
        assertThat(portfolioView.positions().getFirst().symbol()).isEqualTo("AAPL");

        TradeExecutionResult sellResult = tradeExecutionService.sell(
                1001L,
                2002L,
                "AAPL",
                buyResult.executedQuantity()
        );
        assertThat(sellResult.side().name()).isEqualTo("SELL");
        assertThat(sellResult.remainingPositionQuantity()).isEqualByComparingTo("0.00000000");

        PortfolioView afterSell = portfolioQueryService.getPortfolio(1001L, 2002L);
        assertThat(afterSell.positions()).isEmpty();

        TradeHistoryView tradeHistoryView = tradeHistoryQueryService.getHistory(1001L, 2002L, 10);
        assertThat(tradeHistoryView.entries()).hasSize(2);
        assertThat(tradeHistoryView.entries().get(0).side().name()).isEqualTo("SELL");
        assertThat(tradeHistoryView.entries().get(1).side().name()).isEqualTo("BUY");
    }

    @Test
    void liquidatesLeveragedPositionWhenIsolatedEquityIsExhausted() {
        TradeExecutionResult buyResult = tradeExecutionService.buy(1001L, 2002L, "AAPL", new BigDecimal("5"), 10);
        assertThat(buyResult.marginAmount()).isEqualByComparingTo("100.0000");

        quoteRepository.save(new StockQuote("US", "AAPL", new BigDecimal("180.00"), Instant.now()), Duration.ofSeconds(60));
        autoLiquidationService.liquidateExhaustedPositions(
                new StockQuote("US", "AAPL", new BigDecimal("180.00"), Instant.now())
        );

        PortfolioView portfolioView = portfolioQueryService.getPortfolio(1001L, 2002L);
        assertThat(portfolioView.positions()).isEmpty();

        TradeHistoryView tradeHistoryView = tradeHistoryQueryService.getHistory(1001L, 2002L, 10);
        assertThat(tradeHistoryView.entries()).hasSize(2);
        assertThat(tradeHistoryView.entries().get(0).side().name()).isEqualTo("SELL");

        BalanceView balanceView = balanceQueryService.getBalance(1001L, 2002L);
        assertThat(balanceView.cashBalance()).isEqualByComparingTo("9900.0000");
    }
}
