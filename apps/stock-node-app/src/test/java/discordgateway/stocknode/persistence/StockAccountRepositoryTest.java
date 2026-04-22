package discordgateway.stocknode.persistence;

import discordgateway.stocknode.persistence.entity.StockAccountEntity;
import discordgateway.stocknode.persistence.repository.StockAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
class StockAccountRepositoryTest {

    @Autowired
    private StockAccountRepository stockAccountRepository;

    @Test
    void savesAndFindsAccountByGuildAndUser() {
        StockAccountEntity saved = stockAccountRepository.saveAndFlush(
                StockAccountEntity.create(1L, 2L)
        );

        assertThat(saved.getId()).isNotNull();
        assertThat(stockAccountRepository.findByGuildIdAndUserId(1L, 2L))
                .isPresent()
                .get()
                .extracting(StockAccountEntity::getCashBalance)
                .isEqualTo(saved.getCashBalance());
    }

    @Test
    void rejectsDuplicateGuildAndUserCombination() {
        stockAccountRepository.saveAndFlush(StockAccountEntity.create(10L, 20L));

        assertThatThrownBy(() ->
                stockAccountRepository.saveAndFlush(StockAccountEntity.create(10L, 20L))
        ).isInstanceOf(DataIntegrityViolationException.class);
    }
}
