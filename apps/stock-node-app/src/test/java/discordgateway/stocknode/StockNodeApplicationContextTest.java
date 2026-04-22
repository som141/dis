package discordgateway.stocknode;

import discordgateway.stocknode.application.StockAccountApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class StockNodeApplicationContextTest {

    @Autowired
    private StockAccountApplicationService stockAccountApplicationService;

    @Test
    void contextLoads() {
        assertThat(stockAccountApplicationService).isNotNull();
    }
}
