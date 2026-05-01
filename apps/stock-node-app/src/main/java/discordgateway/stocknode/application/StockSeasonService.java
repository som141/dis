package discordgateway.stocknode.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class StockSeasonService {

    public static final ZoneId SEASON_ZONE = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter SEASON_KEY_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");

    private final Clock clock;

    public StockSeasonService(Clock clock) {
        this.clock = clock;
    }

    public String currentSeasonKey() {
        return seasonKeyAt(clock.instant());
    }

    public String seasonKeyAt(Instant instant) {
        return YearMonth.from(ZonedDateTime.ofInstant(instant, SEASON_ZONE)).format(SEASON_KEY_FORMATTER);
    }

    public Instant dayWindowStart(Instant instant) {
        return LocalDate.ofInstant(instant, SEASON_ZONE)
                .atStartOfDay(SEASON_ZONE)
                .toInstant();
    }

    public Instant weekWindowStart(Instant instant) {
        LocalDate now = LocalDate.ofInstant(instant, SEASON_ZONE);
        return now.with(java.time.DayOfWeek.MONDAY)
                .atStartOfDay(SEASON_ZONE)
                .toInstant();
    }
}
