package discordgateway.gateway.messaging;

import discordgateway.gateway.interaction.InteractionResponseContext;
import discordgateway.gateway.interaction.InteractionResponseEditor;
import discordgateway.gateway.interaction.PendingInteractionRepository;
import discordgateway.stock.event.StockCommandResultEvent;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RabbitStockCommandResultListenerTest {

    @Test
    void editsPendingInteractionWhenResultArrives() {
        PendingInteractionRepository repository = mock(PendingInteractionRepository.class);
        InteractionResponseEditor editor = mock(InteractionResponseEditor.class);
        InteractionResponseContext context = new InteractionResponseContext(
                "token",
                "stock",
                10L,
                20L,
                1L,
                10_000L
        );
        StockCommandResultEvent event = new StockCommandResultEvent(
                "command-1",
                1,
                1L,
                "stock-node-1",
                "gateway-1",
                10L,
                20L,
                true,
                "done",
                "BALANCE"
        );
        when(repository.take(event.commandId())).thenReturn(context);

        RabbitStockCommandResultListener listener = new RabbitStockCommandResultListener(repository, editor);

        listener.handle(event);

        verify(editor).editOriginal(context, event.commandId(), event.guildId(), event.resultType(), event.message());
    }

    @Test
    void dropsResultWhenPendingInteractionIsMissing() {
        PendingInteractionRepository repository = mock(PendingInteractionRepository.class);
        InteractionResponseEditor editor = mock(InteractionResponseEditor.class);
        StockCommandResultEvent event = new StockCommandResultEvent(
                "command-1",
                1,
                1L,
                "stock-node-1",
                "gateway-1",
                10L,
                20L,
                true,
                "done",
                "BALANCE"
        );
        when(repository.take(event.commandId())).thenReturn(null);

        RabbitStockCommandResultListener listener = new RabbitStockCommandResultListener(repository, editor);

        listener.handle(event);

        verify(editor, never()).editOriginal(any(), anyString(), anyLong(), anyString(), anyString());
    }
}
