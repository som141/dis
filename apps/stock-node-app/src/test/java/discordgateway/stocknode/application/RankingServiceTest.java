package discordgateway.stocknode.application;

import discordgateway.stocknode.bootstrap.StockQuoteProperties;
import discordgateway.stocknode.cache.RankingCacheRepository;
import discordgateway.stocknode.persistence.entity.AccountSnapshotEntity;
import discordgateway.stocknode.persistence.entity.StockAccountEntity;
import discordgateway.stocknode.persistence.repository.AllowanceLedgerRepository;
import discordgateway.stocknode.persistence.repository.StockAccountRepository;
import discordgateway.stocknode.quote.service.QuoteUsage;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RankingServiceTest {

    @Test
    void computesAllTimeRankingFromAllowancePrincipal() {
        StockAccountRepository stockAccountRepository = mock(StockAccountRepository.class);
        AllowanceLedgerRepository allowanceLedgerRepository = mock(AllowanceLedgerRepository.class);
        DailyAllowanceService dailyAllowanceService = mock(DailyAllowanceService.class);
        StockAccountApplicationService stockAccountApplicationService = mock(StockAccountApplicationService.class);
        PortfolioService portfolioService = mock(PortfolioService.class);
        SnapshotService snapshotService = mock(SnapshotService.class);
        RankingCacheRepository rankingCacheRepository = mock(RankingCacheRepository.class);
        StockQuoteProperties stockQuoteProperties = new StockQuoteProperties();
        stockQuoteProperties.setRankFreshness(Duration.ofMinutes(5));

        StockAccountEntity top = StockAccountEntity.create(1001L, 2002L);
        top.updateCashBalance(new BigDecimal("12000.0000"));
        setId(top, 1L);

        StockAccountEntity second = StockAccountEntity.create(1001L, 2003L);
        second.updateCashBalance(new BigDecimal("10500.0000"));
        setId(second, 2L);

        when(stockAccountApplicationService.currentSeasonKey()).thenReturn("2026-05");
        when(rankingCacheRepository.find(1001L, RankingPeriod.ALL, "2026-05")).thenReturn(Optional.empty());
        when(stockAccountApplicationService.findActiveSeasonAccountsByGuildId(1001L)).thenReturn(List.of(top, second));
        when(portfolioService.build(top, QuoteUsage.RANK)).thenReturn(new PortfolioView(
                1L,
                1001L,
                2002L,
                new BigDecimal("12000.0000"),
                BigDecimal.ZERO.setScale(4),
                BigDecimal.ZERO.setScale(4),
                new BigDecimal("12000.0000"),
                BigDecimal.ZERO.setScale(4),
                List.of()
        ));
        when(portfolioService.build(second, QuoteUsage.RANK)).thenReturn(new PortfolioView(
                2L,
                1001L,
                2003L,
                new BigDecimal("10500.0000"),
                BigDecimal.ZERO.setScale(4),
                BigDecimal.ZERO.setScale(4),
                new BigDecimal("10500.0000"),
                BigDecimal.ZERO.setScale(4),
                List.of()
        ));
        when(allowanceLedgerRepository.sumAmountByAccountId(1L)).thenReturn(new BigDecimal("10000.0000"));
        when(allowanceLedgerRepository.sumAmountByAccountId(2L)).thenReturn(new BigDecimal("10000.0000"));

        RankingService rankingService = new RankingService(
                stockAccountRepository,
                allowanceLedgerRepository,
                dailyAllowanceService,
                stockAccountApplicationService,
                portfolioService,
                snapshotService,
                rankingCacheRepository,
                stockQuoteProperties,
                Clock.fixed(Instant.parse("2026-05-01T00:00:00Z"), ZoneOffset.UTC)
        );

        RankingView rankingView = rankingService.getRanking(1001L, "all");

        assertThat(rankingView.entries()).hasSize(2);
        assertThat(rankingView.entries().getFirst().userId()).isEqualTo(2002L);
        assertThat(rankingView.entries().getFirst().returnRatePercent()).isEqualByComparingTo("20.0000");
        assertThat(rankingView.entries().get(1).returnRatePercent()).isEqualByComparingTo("5.0000");
        verify(rankingCacheRepository).save(any(RankingView.class), eq(stockQuoteProperties.getRankFreshness()));
    }

    @Test
    void usesSnapshotBaselineForDayRanking() {
        StockAccountRepository stockAccountRepository = mock(StockAccountRepository.class);
        AllowanceLedgerRepository allowanceLedgerRepository = mock(AllowanceLedgerRepository.class);
        DailyAllowanceService dailyAllowanceService = mock(DailyAllowanceService.class);
        StockAccountApplicationService stockAccountApplicationService = mock(StockAccountApplicationService.class);
        PortfolioService portfolioService = mock(PortfolioService.class);
        SnapshotService snapshotService = mock(SnapshotService.class);
        RankingCacheRepository rankingCacheRepository = mock(RankingCacheRepository.class);
        StockQuoteProperties stockQuoteProperties = new StockQuoteProperties();

        StockAccountEntity account = StockAccountEntity.create(1001L, 2002L);
        account.updateCashBalance(new BigDecimal("10150.0000"));
        setId(account, 1L);

        when(stockAccountApplicationService.currentSeasonKey()).thenReturn("2026-05");
        when(rankingCacheRepository.find(1001L, RankingPeriod.DAY, "2026-05")).thenReturn(Optional.empty());
        when(stockAccountApplicationService.findActiveSeasonAccountsByGuildId(1001L)).thenReturn(List.of(account));
        when(portfolioService.build(account, QuoteUsage.RANK)).thenReturn(new PortfolioView(
                1L,
                1001L,
                2002L,
                new BigDecimal("10150.0000"),
                BigDecimal.ZERO.setScale(4),
                BigDecimal.ZERO.setScale(4),
                new BigDecimal("10150.0000"),
                BigDecimal.ZERO.setScale(4),
                List.of()
        ));
        when(snapshotService.findBaselineSnapshot(eq(account), eq(RankingPeriod.DAY), any())).thenReturn(
                AccountSnapshotEntity.create(
                        account,
                        Instant.parse("2026-05-01T00:00:00Z"),
                        new BigDecimal("10000.0000"),
                        BigDecimal.ZERO.setScale(4),
                        new BigDecimal("10000.0000")
                )
        );

        RankingService rankingService = new RankingService(
                stockAccountRepository,
                allowanceLedgerRepository,
                dailyAllowanceService,
                stockAccountApplicationService,
                portfolioService,
                snapshotService,
                rankingCacheRepository,
                stockQuoteProperties,
                Clock.fixed(Instant.parse("2026-05-01T01:00:00Z"), ZoneOffset.UTC)
        );

        RankingView rankingView = rankingService.getRanking(1001L, "day");

        assertThat(rankingView.entries()).singleElement().satisfies(entry -> {
            assertThat(entry.baselineEquity()).isEqualByComparingTo("10000.0000");
            assertThat(entry.returnRatePercent()).isEqualByComparingTo("1.5000");
        });
    }

    @Test
    void returnsCachedRankingWithoutRecomputing() {
        StockAccountRepository stockAccountRepository = mock(StockAccountRepository.class);
        AllowanceLedgerRepository allowanceLedgerRepository = mock(AllowanceLedgerRepository.class);
        DailyAllowanceService dailyAllowanceService = mock(DailyAllowanceService.class);
        StockAccountApplicationService stockAccountApplicationService = mock(StockAccountApplicationService.class);
        PortfolioService portfolioService = mock(PortfolioService.class);
        SnapshotService snapshotService = mock(SnapshotService.class);
        RankingCacheRepository rankingCacheRepository = mock(RankingCacheRepository.class);
        StockQuoteProperties stockQuoteProperties = new StockQuoteProperties();

        RankingView cached = new RankingView(
                1001L,
                "2026-05",
                "day",
                Instant.parse("2026-05-01T00:00:00Z"),
                List.of(new RankingEntryView(
                        1L,
                        2002L,
                        new BigDecimal("10100.0000"),
                        new BigDecimal("10000.0000"),
                        new BigDecimal("1.0000")
                ))
        );
        when(stockAccountApplicationService.currentSeasonKey()).thenReturn("2026-05");
        when(rankingCacheRepository.find(1001L, RankingPeriod.DAY, "2026-05")).thenReturn(Optional.of(cached));

        RankingService rankingService = new RankingService(
                stockAccountRepository,
                allowanceLedgerRepository,
                dailyAllowanceService,
                stockAccountApplicationService,
                portfolioService,
                snapshotService,
                rankingCacheRepository,
                stockQuoteProperties,
                Clock.fixed(Instant.parse("2026-05-01T01:00:00Z"), ZoneOffset.UTC)
        );

        RankingView rankingView = rankingService.getRanking(1001L, "day");

        assertThat(rankingView).isSameAs(cached);
        verify(stockAccountApplicationService, never()).findActiveSeasonAccountsByGuildId(anyLong());
    }

    private void setId(StockAccountEntity account, long id) {
        try {
            java.lang.reflect.Field field = StockAccountEntity.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(account, id);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
