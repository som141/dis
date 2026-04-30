package discordgateway.stocknode.application;

import discordgateway.stocknode.persistence.entity.StockAccountEntity;
import discordgateway.stocknode.quote.service.QuoteUsage;
import org.springframework.transaction.annotation.Transactional;

public class PortfolioQueryService {

    private final DailyAllowanceService dailyAllowanceService;
    private final PortfolioService portfolioService;

    public PortfolioQueryService(
            DailyAllowanceService dailyAllowanceService,
            PortfolioService portfolioService
    ) {
        this.dailyAllowanceService = dailyAllowanceService;
        this.portfolioService = portfolioService;
    }

    @Transactional
    public PortfolioView getPortfolio(long guildId, long userId) {
        StockAccountEntity account = dailyAllowanceService.ensureSettledAccount(guildId, userId);
        return portfolioService.build(account, QuoteUsage.QUERY);
    }
}
