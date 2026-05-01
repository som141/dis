package discordgateway.stocknode.application;

import org.springframework.scheduling.annotation.Scheduled;

public class SnapshotScheduler {

    private final SnapshotService snapshotService;

    public SnapshotScheduler(SnapshotService snapshotService) {
        this.snapshotService = snapshotService;
    }

    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
    public void captureDailySnapshots() {
        snapshotService.captureDailySnapshots();
    }

    @Scheduled(cron = "0 5 0 * * MON", zone = "Asia/Seoul")
    public void captureWeeklySnapshots() {
        snapshotService.captureWeeklySnapshots();
    }
}
