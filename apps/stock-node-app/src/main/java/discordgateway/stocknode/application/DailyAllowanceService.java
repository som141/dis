package discordgateway.stocknode.application;

import discordgateway.stocknode.persistence.entity.AllowanceLedgerEntity;
import discordgateway.stocknode.persistence.entity.StockAccountEntity;
import discordgateway.stocknode.cache.RankingCacheRepository;
import discordgateway.stocknode.persistence.repository.AllowanceLedgerRepository;
import discordgateway.stocknode.persistence.repository.StockAccountRepository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;

public class DailyAllowanceService {

    private static final BigDecimal MONTHLY_SEED_AMOUNT = new BigDecimal("10000.0000");

    private final StockAccountApplicationService stockAccountApplicationService;
    private final StockAccountRepository stockAccountRepository;
    private final AllowanceLedgerRepository allowanceLedgerRepository;
    private final RankingCacheRepository rankingCacheRepository;
    private final Clock clock;

    public DailyAllowanceService(
            StockAccountApplicationService stockAccountApplicationService,
            StockAccountRepository stockAccountRepository,
            AllowanceLedgerRepository allowanceLedgerRepository,
            RankingCacheRepository rankingCacheRepository,
            Clock clock
    ) {
        this.stockAccountApplicationService = stockAccountApplicationService;
        this.stockAccountRepository = stockAccountRepository;
        this.allowanceLedgerRepository = allowanceLedgerRepository;
        this.rankingCacheRepository = rankingCacheRepository;
        this.clock = clock;
    }

    @Transactional
    public StockAccountEntity ensureSettledAccount(long guildId, long userId) {
        StockAccountEntity account = stockAccountApplicationService.ensureAccountEntity(guildId, userId);
        applyMonthlySeedIfMissing(account);
        return account;
    }

    @Transactional
    public void applyMonthlySeedIfMissing(StockAccountEntity account) {
        boolean alreadyGranted = allowanceLedgerRepository
                .existsByAccountIdAndAllowanceType(
                        account.getId(),
                        AllowanceType.MONTHLY_SEED.name()
                );
        if (alreadyGranted) {
            return;
        }

        Instant now = clock.instant();
        account.addCash(MONTHLY_SEED_AMOUNT);
        stockAccountRepository.save(account);
        allowanceLedgerRepository.save(
                AllowanceLedgerEntity.create(
                        account,
                        MONTHLY_SEED_AMOUNT,
                        AllowanceType.MONTHLY_SEED.name(),
                        now
                )
        );
        rankingCacheRepository.evictGuild(account.getGuildId(), account.getSeasonKey());
    }
}
