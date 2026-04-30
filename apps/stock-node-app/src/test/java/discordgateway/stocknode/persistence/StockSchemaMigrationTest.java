package discordgateway.stocknode.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class StockSchemaMigrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void createsExpectedTables() {
        assertThat(tableExists("STOCK_ACCOUNT")).isTrue();
        assertThat(tableExists("STOCK_POSITION")).isTrue();
        assertThat(tableExists("TRADE_LEDGER")).isTrue();
        assertThat(tableExists("ALLOWANCE_LEDGER")).isTrue();
        assertThat(tableExists("ACCOUNT_SNAPSHOT")).isTrue();
        assertThat(tableExists("STOCK_WATCHLIST")).isTrue();
    }

    private boolean tableExists(String tableName) {
        Integer tableCount = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables where table_name = ?",
                Integer.class,
                tableName
        );
        return tableCount != null && tableCount > 0;
    }
}
