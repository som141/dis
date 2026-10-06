package discordgateway.gateway.presentation.discord;

import discordgateway.gateway.application.MusicApplicationService;
import discordgateway.gateway.application.PlayAutocompleteService;
import discordgateway.gateway.application.StockApplicationService;
import discordgateway.gateway.interaction.PendingInteractionRepository;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DiscordBotListenerManualTest {

    @Test
    void repliesOnlyToInvokerWithoutDispatchingToWorkers() {
        MusicApplicationService music = mock(MusicApplicationService.class);
        StockApplicationService stock = mock(StockApplicationService.class);
        PlayAutocompleteService autocomplete = mock(PlayAutocompleteService.class);
        PendingInteractionRepository pending = mock(PendingInteractionRepository.class);
        DiscordBotListener listener = new DiscordBotListener(music, stock, autocomplete, pending);
        SlashCommandInteractionEvent event = mock(SlashCommandInteractionEvent.class);
        ReplyCallbackAction reply = mock(ReplyCallbackAction.class);
        when(event.isFromGuild()).thenReturn(true);
        when(event.getName()).thenReturn(DiscordCommandCatalog.CMD_MAN);
        when(event.reply(anyString())).thenReturn(reply);
        when(reply.setEphemeral(true)).thenReturn(reply);

        listener.onSlashCommandInteraction(event);

        var responseOrder = inOrder(event, reply);
        responseOrder.verify(event).reply(DiscordCommandCatalog.manual());
        responseOrder.verify(reply).setEphemeral(true);
        responseOrder.verify(reply).queue();
        verifyNoInteractions(music, stock, autocomplete, pending);
    }
}
