package discordgateway.stocknode.application;

import discordgateway.stocknode.bootstrap.StockQuoteProperties;
import discordgateway.stocknode.cache.RankingCacheRepository;
import discordgateway.stocknode.persistence.entity.AccountSnapshotEntity;
import discordgateway.stocknode.persistence.entity.StockAccountEntity;
import discordgateway.stocknode.persistence.repository.AllowanceLedgerRepository;
import discordgateway.stocknode.persistence.repository.StockAccountRepository;
import discordgateway.stocknode.quote.service.QuoteUsage;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

public class RankingService {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    private final StockAccountRepository stockAccountRepository;
    private final AllowanceLedgerRepository allowanceLedgerRepository;
    private final DailyAllowanceService dailyAllowanceService;
    private final StockAccountApplicationService stockAccountApplicationService;
    private final PortfolioService portfolioService;
    private final SnapshotService snapshotService;
    private final RankingCacheRepository rankingCacheRepository;
    private final StockQuoteProperties stockQuoteProperties;
    private final Clock clock;

    public RankingService(
            StockAccountRepository stockAccountRepository,
            AllowanceLedgerRepository allowanceLedgerRepository,
            DailyAllowanceService dailyAllowanceService,
            StockAccountApplicationService stockAccountApplicationService,
            PortfolioService portfolioService,
            SnapshotService snapshotService,
            RankingCacheRepository rankingCacheRepository,
            StockQuoteProperties stockQuoteProperties,
            Clock clock
    ) {
        this.stockAccountRepository = stockAccountRepository;
        this.allowanceLedgerRepository = allowanceLedgerRepository;
        this.dailyAllowanceService = dailyAllowanceService;
        this.stockAccountApplicationService = stockAccountApplicationService;
        this.portfolioService = portfolioService;
        this.snapshotService = snapshotService;
        this.rankingCacheRepository = rankingCacheRepository;
        this.stockQuoteProperties = stockQuoteProperties;
        this.clock = clock;
    }

    @Transactional
    public RankingView getRanking(long guildId, String rawPeriod) {
        RankingPeriod rankingPeriod = RankingPeriod.from(rawPeriod);
        String seasonKey = stockAccountApplicationService.currentSeasonKey();
        return rankingCacheRepository.find(guildId, rankingPeriod, seasonKey)
                .orElseGet(() -> computeAndCache(guildId, rankingPeriod, seasonKey));
    }

    private RankingView computeAndCache(long guildId, RankingPeriod rankingPeriod, String seasonKey) {
        Instant now = clock.instant();
        List<RankingEntryView> entries = stockAccountApplicationService.findActiveSeasonAccountsByGuildId(guildId).stream()
                .map(account -> toEntry(account, rankingPeriod, now))
                .sorted(Comparator.comparing(RankingEntryView::returnRatePercent).reversed()
                        .thenComparing(RankingEntryView::userId))
                .toList();

        RankingView rankingView = new RankingView(
                guildId,
                seasonKey,
                rankingPeriod.name().toLowerCase(),
                now,
                entries
        );
        rankingCacheRepository.save(rankingView, stockQuoteProperties.getRankFreshness());
        return rankingView;
    }

    private RankingEntryView toEntry(StockAccountEntity account, RankingPeriod rankingPeriod, Instant now) {
        dailyAllowanceService.applyMonthlySeedIfMissing(account);
        PortfolioView portfolioView = portfolioService.build(account, QuoteUsage.RANK);
        BigDecimal baselineEquity = resolveBaselineEquity(account, rankingPeriod, portfolioView, now);
        BigDecimal returnRatePercent = computeReturnRatePercent(portfolioView.totalEquity(), baselineEquity);
        return new RankingEntryView(
                account.getId(),
                account.getUserId(),
                portfolioView.totalEquity(),
                baselineEquity,
                returnRatePercent
        );
    }

    private BigDecimal resolveBaselineEquity(
            StockAccountEntity account,
            RankingPeriod rankingPeriod,
            PortfolioView portfolioView,
            Instant now
    ) {
        if (!rankingPeriod.usesSnapshotBaseline()) {
            BigDecimal allowanceTotal = allowanceLedgerRepository.sumAmountByAccountId(account.getId());
            return normalizeCash(allowanceTotal.signum() > 0 ? allowanceTotal : portfolioView.totalEquity());
        }

        AccountSnapshotEntity snapshot = snapshotService.findBaselineSnapshot(account, rankingPeriod, now);
        if (snapshot == null) {
            snapshotService.captureCurrentSnapshot(account);
            return portfolioView.totalEquity();
        }
        return normalizeCash(snapshot.getTotalEquity());
    }

    private BigDecimal computeReturnRatePercent(BigDecimal totalEquity, BigDecimal baselineEquity) {
        if (baselineEquity == null || baselineEquity.signum() <= 0) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        return totalEquity.subtract(baselineEquity)
                .divide(baselineEquity, 8, RoundingMode.HALF_UP)
                .multiply(ONE_HUNDRED)
                .setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal normalizeCash(BigDecimal value) {
        return value.setScale(4, RoundingMode.HALF_UP);
    }
}
