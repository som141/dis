package discordgateway.stocknode.application;

import discordgateway.stocknode.persistence.repository.StockAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class StockAccountApplicationServiceTest {

    @Autowired
    private StockAccountApplicationService stockAccountApplicationService;

    @Autowired
    private StockAccountRepository stockAccountRepository;

    @Test
    void ensureAccountCreatesAndReusesExistingAccount() {
        StockAccountSummary created = stockAccountApplicationService.ensureAccount(1001L, 2002L);
        StockAccountSummary foundAgain = stockAccountApplicationService.ensureAccount(1001L, 2002L);

        assertThat(foundAgain.accountId()).isEqualTo(created.accountId());
        assertThat(stockAccountRepository.count()).isEqualTo(1);
        assertThat(foundAgain.cashBalance()).isEqualByComparingTo("0");
    }

    @Test
    void findAccountReturnsEmptyWhenMissing() {
        assertThat(stockAccountApplicationService.findAccount(9999L, 8888L)).isEmpty();
    }
}
