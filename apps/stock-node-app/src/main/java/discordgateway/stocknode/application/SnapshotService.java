package discordgateway.stocknode.application;

import discordgateway.stocknode.persistence.entity.AccountSnapshotEntity;
import discordgateway.stocknode.persistence.entity.StockAccountEntity;
import discordgateway.stocknode.persistence.repository.AccountSnapshotRepository;
import discordgateway.stocknode.persistence.repository.StockAccountRepository;
import discordgateway.stocknode.quote.service.QuoteUsage;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

public class SnapshotService {

    private final StockAccountRepository stockAccountRepository;
    private final AccountSnapshotRepository accountSnapshotRepository;
    private final DailyAllowanceService dailyAllowanceService;
    private final PortfolioService portfolioService;
    private final Clock clock;

    public SnapshotService(
            StockAccountRepository stockAccountRepository,
            AccountSnapshotRepository accountSnapshotRepository,
            DailyAllowanceService dailyAllowanceService,
            PortfolioService portfolioService,
            Clock clock
    ) {
        this.stockAccountRepository = stockAccountRepository;
        this.accountSnapshotRepository = accountSnapshotRepository;
        this.dailyAllowanceService = dailyAllowanceService;
        this.portfolioService = portfolioService;
        this.clock = clock;
    }

    @Transactional
    public AccountSnapshotEntity captureCurrentSnapshot(StockAccountEntity account) {
        dailyAllowanceService.applyDailyAllowanceIfDue(account);
        PortfolioView portfolioView = portfolioService.build(account, QuoteUsage.RANK);
        return accountSnapshotRepository.save(AccountSnapshotEntity.create(
                account,
                clock.instant(),
                portfolioView.cashBalance(),
                portfolioView.totalMarketValue(),
                portfolioView.totalEquity()
        ));
    }

    @Transactional
    public int captureDailySnapshots() {
        return captureSnapshots(stockAccountRepository.findAll());
    }

    @Transactional
    public int captureWeeklySnapshots() {
        return captureSnapshots(stockAccountRepository.findAll());
    }

    @Transactional
    public int captureSnapshotsForGuild(long guildId) {
        return captureSnapshots(stockAccountRepository.findAllByGuildIdOrderByIdAsc(guildId));
    }

    @Transactional(readOnly = true)
    public AccountSnapshotEntity findBaselineSnapshot(StockAccountEntity account, RankingPeriod rankingPeriod, Instant now) {
        return accountSnapshotRepository
                .findTopByAccountIdAndSnapshotAtGreaterThanEqualOrderBySnapshotAtAsc(
                        account.getId(),
                        rankingPeriod.windowStart(now)
                )
                .orElse(null);
    }

    private int captureSnapshots(List<StockAccountEntity> accounts) {
        int count = 0;
        for (StockAccountEntity account : accounts) {
            captureCurrentSnapshot(account);
            count++;
        }
        return count;
    }
}
