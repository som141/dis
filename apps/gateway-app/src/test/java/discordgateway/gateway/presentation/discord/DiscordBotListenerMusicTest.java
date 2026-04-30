package discordgateway.gateway.presentation.discord;

import discordgateway.common.command.MusicCommand;
import discordgateway.common.command.MusicCommandEnvelope;
import discordgateway.common.command.MusicCommandMessage;
import discordgateway.common.command.MusicCommandResponseMode;
import discordgateway.gateway.application.MusicApplicationService;
import discordgateway.gateway.application.PlayAutocompleteService;
import discordgateway.gateway.application.StockApplicationService;
import discordgateway.gateway.interaction.InteractionResponseContext;
import discordgateway.gateway.interaction.PendingInteractionRepository;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThatCode;

class DiscordBotListenerMusicTest {

    @Test
    void doesNotDispatchMusicCommandBeforeDeferredReplySucceeds() {
        MusicApplicationService musicApplicationService = mock(MusicApplicationService.class);
        StockApplicationService stockApplicationService = mock(StockApplicationService.class);
        PlayAutocompleteService playAutocompleteService = mock(PlayAutocompleteService.class);
        PendingInteractionRepository pendingInteractionRepository = mock(PendingInteractionRepository.class);
        DiscordBotListener listener = new DiscordBotListener(
                musicApplicationService,
                stockApplicationService,
                playAutocompleteService,
                pendingInteractionRepository
        );

        SlashCommandInteractionEvent event = mock(SlashCommandInteractionEvent.class);
        Guild guild = mock(Guild.class);
        JDA jda = mock(JDA.class);
        MessageChannelUnion channel = mock(MessageChannelUnion.class);
        ReplyCallbackAction replyCallbackAction = mock(ReplyCallbackAction.class);

        when(event.isFromGuild()).thenReturn(true);
        when(event.getName()).thenReturn(DiscordCommandCatalog.CMD_SKIP);
        when(event.getGuild()).thenReturn(guild);
        when(event.getJDA()).thenReturn(jda);
        when(event.getChannel()).thenReturn(channel);
        when(guild.getIdLong()).thenReturn(10L);
        when(jda.getGuildById(10L)).thenReturn(guild);
        when(event.deferReply(true)).thenReturn(replyCallbackAction);

        MusicCommandEnvelope envelope = new MusicCommandEnvelope(
                new MusicCommandMessage(
                        "music-command-1",
                        1,
                        1L,
                        "gateway-1",
                        new MusicCommand.Skip(10L)
                ),
                "gateway-1",
                MusicCommandResponseMode.EPHEMERAL
        );
        when(musicApplicationService.prepareSkip(guild)).thenReturn(envelope);
        when(musicApplicationService.dispatch(envelope)).thenReturn(CompletableFuture.completedFuture(null));

        doAnswer(invocation -> null).when(replyCallbackAction).queue(any(), any());

        listener.onSlashCommandInteraction(event);

        verify(musicApplicationService).prepareSkip(guild);
        verify(musicApplicationService, never()).dispatch(envelope);
        verify(pendingInteractionRepository, never()).put(eq("music-command-1"), any(InteractionResponseContext.class));
    }

    @Test
    void storesPendingInteractionBeforeDispatchingMusicCommand() {
        MusicApplicationService musicApplicationService = mock(MusicApplicationService.class);
        StockApplicationService stockApplicationService = mock(StockApplicationService.class);
        PlayAutocompleteService playAutocompleteService = mock(PlayAutocompleteService.class);
        PendingInteractionRepository pendingInteractionRepository = mock(PendingInteractionRepository.class);
        DiscordBotListener listener = new DiscordBotListener(
                musicApplicationService,
                stockApplicationService,
                playAutocompleteService,
                pendingInteractionRepository
        );

        SlashCommandInteractionEvent event = mock(SlashCommandInteractionEvent.class);
        Guild guild = mock(Guild.class);
        JDA jda = mock(JDA.class);
        MessageChannelUnion channel = mock(MessageChannelUnion.class);
        ReplyCallbackAction replyCallbackAction = mock(ReplyCallbackAction.class);
        InteractionHook hook = mock(InteractionHook.class);

        when(event.isFromGuild()).thenReturn(true);
        when(event.getName()).thenReturn(DiscordCommandCatalog.CMD_SKIP);
        when(event.getGuild()).thenReturn(guild);
        when(event.getJDA()).thenReturn(jda);
        when(event.getChannel()).thenReturn(channel);
        when(event.getToken()).thenReturn("token-1");
        when(channel.getIdLong()).thenReturn(30L);
        when(channel.getId()).thenReturn("30");
        when(guild.getIdLong()).thenReturn(10L);
        when(guild.getId()).thenReturn("10");
        when(jda.getGuildById(10L)).thenReturn(guild);
        when(event.deferReply(true)).thenReturn(replyCallbackAction);

        MusicCommandEnvelope envelope = new MusicCommandEnvelope(
                new MusicCommandMessage(
                        "music-command-2",
                        1,
                        1L,
                        "gateway-1",
                        new MusicCommand.Skip(10L)
                ),
                "gateway-1",
                MusicCommandResponseMode.EPHEMERAL
        );
        when(musicApplicationService.prepareSkip(guild)).thenReturn(envelope);
        when(musicApplicationService.dispatch(envelope)).thenReturn(CompletableFuture.completedFuture(null));

        doAnswer(invocation -> {
            Consumer<InteractionHook> success = invocation.getArgument(0);
            success.accept(hook);
            return null;
        }).when(replyCallbackAction).queue(any(), any());

        listener.onSlashCommandInteraction(event);

        InOrder inOrder = inOrder(pendingInteractionRepository, musicApplicationService);
        inOrder.verify(pendingInteractionRepository).put(eq("music-command-2"), any(InteractionResponseContext.class));
        inOrder.verify(musicApplicationService).dispatch(envelope);
    }

    @Test
    void doesNotHangWhenPendingInteractionStorageFails() {
        MusicApplicationService musicApplicationService = mock(MusicApplicationService.class);
        StockApplicationService stockApplicationService = mock(StockApplicationService.class);
        PlayAutocompleteService playAutocompleteService = mock(PlayAutocompleteService.class);
        PendingInteractionRepository pendingInteractionRepository = mock(PendingInteractionRepository.class);
        DiscordBotListener listener = new DiscordBotListener(
                musicApplicationService,
                stockApplicationService,
                playAutocompleteService,
                pendingInteractionRepository
        );

        SlashCommandInteractionEvent event = mock(SlashCommandInteractionEvent.class);
        Guild guild = mock(Guild.class);
        JDA jda = mock(JDA.class);
        MessageChannelUnion channel = mock(MessageChannelUnion.class);
        ReplyCallbackAction replyCallbackAction = mock(ReplyCallbackAction.class);
        InteractionHook hook = mock(InteractionHook.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);

        when(event.isFromGuild()).thenReturn(true);
        when(event.getName()).thenReturn(DiscordCommandCatalog.CMD_SKIP);
        when(event.getGuild()).thenReturn(guild);
        when(event.getJDA()).thenReturn(jda);
        when(event.getChannel()).thenReturn(channel);
        when(event.getToken()).thenReturn("token-1");
        when(channel.getIdLong()).thenReturn(30L);
        when(channel.getId()).thenReturn("30");
        when(guild.getIdLong()).thenReturn(10L);
        when(guild.getId()).thenReturn("10");
        when(jda.getGuildById(10L)).thenReturn(guild);
        when(event.deferReply(true)).thenReturn(replyCallbackAction);

        MusicCommandEnvelope envelope = new MusicCommandEnvelope(
                new MusicCommandMessage(
                        "music-command-3",
                        1,
                        1L,
                        "gateway-1",
                        new MusicCommand.Skip(10L)
                ),
                "gateway-1",
                MusicCommandResponseMode.EPHEMERAL
        );
        when(musicApplicationService.prepareSkip(guild)).thenReturn(envelope);
        doThrow(new IllegalStateException("redis down"))
                .when(pendingInteractionRepository)
                .put(eq("music-command-3"), any(InteractionResponseContext.class));

        doAnswer(invocation -> {
            Consumer<InteractionHook> success = invocation.getArgument(0);
            success.accept(hook);
            return null;
        }).when(replyCallbackAction).queue(any(), any());

        assertThatCode(() -> listener.onSlashCommandInteraction(event)).doesNotThrowAnyException();
        verify(musicApplicationService, never()).dispatch(envelope);
    }
}
