package discordgateway.stocknode.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import discordgateway.stock.command.StockCommand;
import discordgateway.stock.command.StockCommandEnvelope;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class StockCommandContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void serializesBuyAmountContract() throws Exception {
        StockCommandEnvelope envelope = new StockCommandEnvelope(
                "cmd-1",
                1,
                1_234L,
                "gateway",
                new StockCommand.Buy(1001L, 2002L, "AAPL", new BigDecimal("1000.00")),
                "gateway-1"
        );

        StockCommandEnvelope restored = objectMapper.readValue(
                objectMapper.writeValueAsBytes(envelope),
                StockCommandEnvelope.class
        );

        assertThat(restored.command()).isInstanceOf(StockCommand.Buy.class);
        StockCommand.Buy buy = (StockCommand.Buy) restored.command();
        assertThat(buy.amount()).isEqualByComparingTo("1000.00");
    }

    @Test
    void serializesRankPeriodContract() throws Exception {
        StockCommandEnvelope envelope = new StockCommandEnvelope(
                "cmd-2",
                1,
                1_234L,
                "gateway",
                new StockCommand.Rank(1001L, 2002L, "day"),
                "gateway-1"
        );

        StockCommandEnvelope restored = objectMapper.readValue(
                objectMapper.writeValueAsBytes(envelope),
                StockCommandEnvelope.class
        );

        assertThat(restored.command()).isInstanceOf(StockCommand.Rank.class);
        StockCommand.Rank rank = (StockCommand.Rank) restored.command();
        assertThat(rank.period()).isEqualTo("day");
    }
}
