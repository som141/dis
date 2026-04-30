package discordgateway.stocknode.messaging;

import discordgateway.stock.command.StockCommand;
import discordgateway.stock.command.StockCommandEnvelope;
import discordgateway.stock.event.StockCommandResultEvent;
import discordgateway.stocknode.application.InvalidTradeArgumentException;
import discordgateway.stocknode.application.StockCommandApplicationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockCommandListenerTest {

    @Mock
    private StockCommandApplicationService stockCommandApplicationService;

    @Mock
    private StockCommandResultPublisher stockCommandResultPublisher;

    @Test
    void publishesSuccessEventOnHandledCommand() {
        StockCommandListener listener = new StockCommandListener(
                stockCommandApplicationService,
                stockCommandResultPublisher
        );
        StockCommandEnvelope envelope = envelope();
        StockCommandResultEvent successEvent = event(true, "BALANCE");
        when(stockCommandApplicationService.handle(envelope)).thenReturn(successEvent);

        listener.handle(envelope);

        verify(stockCommandResultPublisher).publish(successEvent);
    }

    @Test
    void publishesFailureEventWhenHandlerThrows() {
        StockCommandListener listener = new StockCommandListener(
                stockCommandApplicationService,
                stockCommandResultPublisher
        );
        StockCommandEnvelope envelope = envelope();
        InvalidTradeArgumentException exception = new InvalidTradeArgumentException("bad request");
        StockCommandResultEvent failureEvent = event(false, "FAILED");
        when(stockCommandApplicationService.handle(envelope)).thenThrow(exception);
        when(stockCommandApplicationService.failure(envelope, exception)).thenReturn(failureEvent);

        listener.handle(envelope);

        verify(stockCommandResultPublisher).publish(failureEvent);
    }

    private StockCommandEnvelope envelope() {
        return new StockCommandEnvelope(
                "cmd-1",
                1,
                1_234L,
                "gateway",
                new StockCommand.Balance(1001L, 2002L),
                "gateway-1"
        );
    }

    private StockCommandResultEvent event(boolean success, String resultType) {
        return new StockCommandResultEvent(
                "cmd-1",
                1,
                1_234L,
                "stock-node-1",
                "gateway-1",
                1001L,
                2002L,
                success,
                "message",
                resultType
        );
    }
}
