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
import java.time.LocalDate;
import java.time.ZoneOffset;

public class DailyAllowanceService {

    private static final BigDecimal DAILY_ALLOWANCE_AMOUNT = new BigDecimal("10000.0000");

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
        applyDailyAllowanceIfDue(account);
        return account;
    }

    @Transactional
    public void applyDailyAllowanceIfDue(StockAccountEntity account) {
        Instant now = clock.instant();
        Instant startOfDay = LocalDate.ofInstant(now, ZoneOffset.UTC)
                .atStartOfDay()
                .toInstant(ZoneOffset.UTC);
        Instant startOfNextDay = startOfDay.plusSeconds(24 * 60 * 60);

        boolean alreadyGranted = allowanceLedgerRepository
                .existsByAccountIdAndAllowanceTypeAndOccurredAtGreaterThanEqualAndOccurredAtLessThan(
                        account.getId(),
                        AllowanceType.DAILY.name(),
                        startOfDay,
                        startOfNextDay
                );
        if (alreadyGranted) {
            return;
        }

        account.addCash(DAILY_ALLOWANCE_AMOUNT);
        stockAccountRepository.save(account);
        allowanceLedgerRepository.save(
                AllowanceLedgerEntity.create(
                        account,
                        DAILY_ALLOWANCE_AMOUNT,
                        AllowanceType.DAILY.name(),
                        now
                )
        );
        rankingCacheRepository.evictGuild(account.getGuildId());
    }
}
