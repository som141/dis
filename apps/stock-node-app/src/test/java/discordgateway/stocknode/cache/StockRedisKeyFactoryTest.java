package discordgateway.stocknode.cache;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class StockRedisKeyFactoryTest {

    private final StockRedisKeyFactory stockRedisKeyFactory = new StockRedisKeyFactory();

    @Test
    void buildsNormalizedRedisKeys() {
        assertThat(stockRedisKeyFactory.quoteKey("US", "aapl"))
                .isEqualTo("stock:quote:US:AAPL");
        assertThat(stockRedisKeyFactory.quoteLockKey("US", "aapl"))
                .isEqualTo("stock:quote:lock:US:AAPL");
        assertThat(stockRedisKeyFactory.providerMinuteLimitKey("Mock", Instant.parse("2026-04-22T07:05:31Z")))
                .isEqualTo("stock:provider:mock:minute:202604220705");
        assertThat(stockRedisKeyFactory.providerDayLimitKey("Mock", LocalDate.parse("2026-04-22")))
                .isEqualTo("stock:provider:mock:day:2026-04-22");
        assertThat(stockRedisKeyFactory.rankKey(1234L, "Daily"))
                .isEqualTo("stock:rank:1234:daily");
    }
}
