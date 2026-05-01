package discordgateway.stocknode.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

public class MonthlySeasonScheduler {

    private static final Logger log = LoggerFactory.getLogger(MonthlySeasonScheduler.class);

    private final StockSeasonService stockSeasonService;

    public MonthlySeasonScheduler(StockSeasonService stockSeasonService) {
        this.stockSeasonService = stockSeasonService;
    }

    @Scheduled(cron = "0 0 0 1 * *", zone = "Asia/Seoul")
    public void onMonthlySeasonBoundary() {
        log.info("stock monthly season boundary reached. activeSeason={}", stockSeasonService.currentSeasonKey());
    }
}
