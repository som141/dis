package discordgateway.stocknode.application;

import discordgateway.stocknode.persistence.entity.StockAccountEntity;
import discordgateway.stocknode.persistence.entity.StockPositionEntity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class StockPositionWriteModelTest {

    @Test
    void recalculatesAverageCostOnBuyAndKeepsItOnPartialSell() {
        StockAccountEntity account = StockAccountEntity.create(1001L, 2002L);
        StockPositionEntity position = StockPositionEntity.create(account, "AAPL");

        position.applyBuy(new BigDecimal("2.00000000"), new BigDecimal("100.00"));
        position.applyBuy(new BigDecimal("1.00000000"), new BigDecimal("130.00"));

        assertThat(position.getQuantity()).isEqualByComparingTo("3.00000000");
        assertThat(position.getAverageCost()).isEqualByComparingTo("110.0000");

        position.applySell(new BigDecimal("1.50000000"));

        assertThat(position.getQuantity()).isEqualByComparingTo("1.50000000");
        assertThat(position.getAverageCost()).isEqualByComparingTo("110.0000");
    }

    @Test
    void becomesEmptyOnFullSell() {
        StockAccountEntity account = StockAccountEntity.create(1001L, 2002L);
        StockPositionEntity position = StockPositionEntity.create(account, "AAPL");

        position.applyBuy(new BigDecimal("5.00000000"), new BigDecimal("100.00"));
        position.applySell(new BigDecimal("5.00000000"));

        assertThat(position.isEmpty()).isTrue();
        assertThat(position.getQuantity()).isEqualByComparingTo("0.00000000");
        assertThat(position.getAverageCost()).isEqualByComparingTo("0.0000");
    }
}
