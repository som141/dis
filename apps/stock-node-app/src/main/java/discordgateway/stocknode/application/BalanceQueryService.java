package discordgateway.stocknode.application;

import discordgateway.stocknode.persistence.entity.StockAccountEntity;
import org.springframework.transaction.annotation.Transactional;

public class BalanceQueryService {

    private final DailyAllowanceService dailyAllowanceService;

    public BalanceQueryService(DailyAllowanceService dailyAllowanceService) {
        this.dailyAllowanceService = dailyAllowanceService;
    }

    @Transactional
    public BalanceView getBalance(long guildId, long userId) {
        StockAccountEntity account = dailyAllowanceService.ensureSettledAccount(guildId, userId);
        return new BalanceView(
                account.getId(),
                account.getGuildId(),
                account.getUserId(),
                account.getCashBalance()
        );
    }
}
