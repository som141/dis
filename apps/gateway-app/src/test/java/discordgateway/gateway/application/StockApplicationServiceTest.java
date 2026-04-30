package discordgateway.gateway.application;

import discordgateway.stock.command.StockCommand;
import discordgateway.stock.command.StockCommandEnvelope;
import discordgateway.stock.messaging.StockCommandBus;
import discordgateway.stock.messaging.StockCommandMessageFactory;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StockApplicationServiceTest {

    @Test
    void preparesQuoteCommandWithNormalizedSymbol() {
        StockApplicationService service = new StockApplicationService(
                envelope -> CompletableFuture.completedFuture(null),
                new StockCommandMessageFactory("gateway-1")
        );

        StockCommandEnvelope envelope = service.prepareQuote(10L, 20L, " aapl ");

        assertThat(envelope.producer()).isEqualTo("gateway-1");
        assertThat(envelope.responseTargetNode()).isEqualTo("gateway-1");
        assertThat(envelope.command()).isEqualTo(new StockCommand.Quote(10L, 20L, "AAPL"));
    }

    @Test
    void preparesBuyCommandWithAmountPayload() {
        StockApplicationService service = new StockApplicationService(
                envelope -> CompletableFuture.completedFuture(null),
                new StockCommandMessageFactory("gateway-1")
        );

        StockCommandEnvelope envelope = service.prepareBuy(10L, 20L, "msft", new BigDecimal("1500.50"));

        assertThat(envelope.command()).isEqualTo(new StockCommand.Buy(
                10L,
                20L,
                "MSFT",
                new BigDecimal("1500.50")
        ));
    }

    @Test
    void dispatchDelegatesToStockCommandBus() {
        StockCommandBus stockCommandBus = mock(StockCommandBus.class);
        StockCommandEnvelope envelope = new StockCommandEnvelope(
                "command-1",
                1,
                1L,
                "gateway-1",
                new StockCommand.Balance(10L, 20L),
                "gateway-1"
        );
        when(stockCommandBus.dispatch(envelope)).thenReturn(CompletableFuture.completedFuture(null));

        StockApplicationService service = new StockApplicationService(
                stockCommandBus,
                new StockCommandMessageFactory("gateway-1")
        );

        service.dispatch(envelope).join();

        verify(stockCommandBus).dispatch(envelope);
    }
}
