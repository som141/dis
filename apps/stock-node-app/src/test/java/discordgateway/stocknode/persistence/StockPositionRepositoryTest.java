package discordgateway.stocknode.persistence;

import discordgateway.stocknode.persistence.entity.StockAccountEntity;
import discordgateway.stocknode.persistence.entity.StockPositionEntity;
import discordgateway.stocknode.persistence.repository.StockAccountRepository;
import discordgateway.stocknode.persistence.repository.StockPositionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
class StockPositionRepositoryTest {

    @Autowired
    private StockAccountRepository stockAccountRepository;

    @Autowired
    private StockPositionRepository stockPositionRepository;

    @Test
    void savesAndFindsPositionByAccountAndSymbol() {
        StockAccountEntity account = stockAccountRepository.saveAndFlush(
                StockAccountEntity.create(30L, 40L)
        );

        StockPositionEntity saved = stockPositionRepository.saveAndFlush(
                StockPositionEntity.create(account, "aapl")
        );

        assertThat(saved.getId()).isNotNull();
        assertThat(stockPositionRepository.findByAccountIdAndSymbol(account.getId(), "AAPL"))
                .isPresent()
                .get()
                .extracting(StockPositionEntity::getSymbol)
                .isEqualTo("AAPL");
    }

    @Test
    void rejectsDuplicateAccountAndSymbolCombination() {
        StockAccountEntity account = stockAccountRepository.saveAndFlush(
                StockAccountEntity.create(50L, 60L)
        );
        stockPositionRepository.saveAndFlush(StockPositionEntity.create(account, "MSFT"));

        assertThatThrownBy(() ->
                stockPositionRepository.saveAndFlush(StockPositionEntity.create(account, "msft"))
        ).isInstanceOf(DataIntegrityViolationException.class);
    }
}
