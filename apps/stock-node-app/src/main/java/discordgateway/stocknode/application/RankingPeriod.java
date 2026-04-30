package discordgateway.stocknode.application;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Locale;

public enum RankingPeriod {
    DAY,
    WEEK,
    ALL;

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
        LocalDate currentDate = LocalDate.ofInstant(now, ZoneOffset.UTC);
        return switch (this) {
            case DAY -> currentDate.atStartOfDay().toInstant(ZoneOffset.UTC);
            case WEEK -> currentDate.with(DayOfWeek.MONDAY).atStartOfDay().toInstant(ZoneOffset.UTC);
            case ALL -> Instant.EPOCH;
        };
    }
}
