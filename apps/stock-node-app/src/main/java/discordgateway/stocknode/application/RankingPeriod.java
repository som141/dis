package discordgateway.stocknode.application;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;

public enum RankingPeriod {
    DAY,
    WEEK,
    ALL;

    private static final ZoneId RANKING_ZONE = ZoneId.of("Asia/Seoul");

    public static RankingPeriod from(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Ranking period is required");
        }
        return valueOf(raw.trim().toUpperCase(Locale.ROOT));
    }

    public boolean usesSnapshotBaseline() {
        return this != ALL;
    }

    public Instant windowStart(Instant now) {
        LocalDate currentDate = LocalDate.ofInstant(now, RANKING_ZONE);
        return switch (this) {
            case DAY -> currentDate.atStartOfDay(RANKING_ZONE).toInstant();
            case WEEK -> currentDate.with(DayOfWeek.MONDAY).atStartOfDay(RANKING_ZONE).toInstant();
            case ALL -> Instant.EPOCH;
        };
    }
}
