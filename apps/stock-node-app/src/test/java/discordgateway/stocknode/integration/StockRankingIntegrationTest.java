package discordgateway.stocknode.integration;

import discordgateway.stocknode.application.RankingService;
import discordgateway.stocknode.application.RankingView;
import discordgateway.stocknode.application.SnapshotService;
import discordgateway.stocknode.application.TradeExecutionService;
import discordgateway.stocknode.bootstrap.StockQuoteProperties;
import discordgateway.stocknode.cache.QuoteRepository;
import discordgateway.stocknode.quote.model.StockQuote;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class StockRankingIntegrationTest extends StockNodeIntegrationTestSupport {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private QuoteRepository quoteRepository;

    @Autowired
    private StockQuoteProperties stockQuoteProperties;

    @Autowired
    private Clock stockClock;

    @Autowired
    private TradeExecutionService tradeExecutionService;

    @Autowired
    private SnapshotService snapshotService;

    @Autowired
    private RankingService rankingService;

    @BeforeEach
    void cleanState() {
        jdbcTemplate.execute("TRUNCATE TABLE account_snapshot, allowance_ledger, trade_ledger, stock_position, stock_account RESTART IDENTITY CASCADE");
        stringRedisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Test
    void ranksAccountsByCurrentReturnAgainstDailySnapshotBaseline() {
        quoteRepository.save(
                new StockQuote("us", "AAPL", new BigDecimal("100.00"), stockClock.instant()),
                stockQuoteProperties.getCacheTtl()
        );

        tradeExecutionService.buy(1001L, 2002L, "AAPL", new BigDecimal("10"), 1);
        snapshotService.captureSnapshotsForGuild(1001L);

        quoteRepository.save(
                new StockQuote("us", "AAPL", new BigDecimal("120.00"), stockClock.instant()),
                stockQuoteProperties.getCacheTtl()
        );

        RankingView rankingView = rankingService.getRanking(1001L, "day");

        assertThat(rankingView.entries()).hasSize(1);
        assertThat(rankingView.entries().getFirst().userId()).isEqualTo(2002L);
        assertThat(rankingView.entries().getFirst().returnRatePercent()).isPositive();
    }
}
