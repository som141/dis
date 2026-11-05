package discordgateway.gateway.presentation.discord;

import discordgateway.gateway.application.MusicApplicationService;
import discordgateway.gateway.application.PlayAutocompleteService;
import discordgateway.gateway.application.StockApplicationService;
import discordgateway.gateway.interaction.PendingInteractionRepository;
import discordgateway.gateway.maple.MapleDiscordService;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class DiscordBotListenerMapleTest {
    @Test
    void connectionLinkIsIssuedOnlyForInvokingDiscordUserInEphemeralReply(){
        MapleDiscordService maple=mock(MapleDiscordService.class);
        DiscordBotListener listener=new DiscordBotListener(mock(MusicApplicationService.class),mock(StockApplicationService.class),
                mock(PlayAutocompleteService.class),mock(PendingInteractionRepository.class),maple);
        SlashCommandInteractionEvent event=mock(SlashCommandInteractionEvent.class);
        User user=mock(User.class);ReplyCallbackAction reply=mock(ReplyCallbackAction.class);
        when(event.isFromGuild()).thenReturn(true);when(event.getName()).thenReturn(DiscordCommandCatalog.CMD_MAPLE);
        when(event.getSubcommandName()).thenReturn(DiscordCommandCatalog.SUB_CONNECT);when(event.getUser()).thenReturn(user);
        when(user.getIdLong()).thenReturn(123456789012345678L);
        when(maple.linkFor(123456789012345678L)).thenReturn("https://maple.example.test/maple/connect?ticket=fixture");
        when(event.reply("개인 메이플 포털 연결 링크: https://maple.example.test/maple/connect?ticket=fixture")).thenReturn(reply);
        when(reply.setEphemeral(true)).thenReturn(reply);
        listener.onSlashCommandInteraction(event);
        verify(maple).linkFor(123456789012345678L);
        verify(reply).setEphemeral(true);
        verify(reply).queue();
    }
}
