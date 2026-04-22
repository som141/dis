package discordgateway.stocknode.integration;

import discordgateway.stocknode.application.StockAccountApplicationService;
import discordgateway.stocknode.application.StockAccountSummary;
import discordgateway.stocknode.persistence.entity.StockAccountEntity;
import discordgateway.stocknode.persistence.repository.StockAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class StockPersistenceIntegrationTest extends StockNodeIntegrationTestSupport {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StockAccountRepository stockAccountRepository;

    @Autowired
    private StockAccountApplicationService stockAccountApplicationService;

    @BeforeEach
    void cleanTables() {
        jdbcTemplate.execute("TRUNCATE TABLE account_snapshot, allowance_ledger, trade_ledger, stock_position, stock_account RESTART IDENTITY CASCADE");
    }

    @Test
    void appliesFlywaySchemaToRealPostgreSql() {
        assertThat(tableExists("stock_account")).isTrue();
        assertThat(tableExists("stock_position")).isTrue();
        assertThat(tableExists("trade_ledger")).isTrue();
        assertThat(tableExists("allowance_ledger")).isTrue();
        assertThat(tableExists("account_snapshot")).isTrue();
    }

    @Test
    void enforcesUniqueGuildAndUserConstraintOnRealPostgreSql() {
        stockAccountRepository.saveAndFlush(StockAccountEntity.create(11L, 22L));

        assertThatThrownBy(() ->
                stockAccountRepository.saveAndFlush(StockAccountEntity.create(11L, 22L))
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void createsAndReusesAccountOnRealPostgreSql() {
        StockAccountSummary created = stockAccountApplicationService.ensureAccount(100L, 200L);
        StockAccountSummary foundAgain = stockAccountApplicationService.ensureAccount(100L, 200L);

        assertThat(foundAgain.accountId()).isEqualTo(created.accountId());
        assertThat(stockAccountRepository.count()).isEqualTo(1);
        assertThat(foundAgain.cashBalance()).isEqualByComparingTo("0");
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
