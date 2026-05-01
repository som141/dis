package discordgateway.gateway.presentation.discord;

import discordgateway.gateway.application.MusicApplicationService;
import discordgateway.gateway.application.PlayAutocompleteService;
import discordgateway.gateway.application.StockApplicationService;
import discordgateway.gateway.interaction.InteractionResponseContext;
import discordgateway.gateway.interaction.PendingInteractionRepository;
import discordgateway.stock.command.StockCommand;
import discordgateway.stock.command.StockCommandEnvelope;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DiscordBotListenerStockTest {

    @Test
    void dispatchesStockQuoteThroughDeferredInteraction() {
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
        User user = mock(User.class);
        MessageChannelUnion channel = mock(MessageChannelUnion.class);
        OptionMapping symbolOption = mock(OptionMapping.class);
        ReplyCallbackAction replyCallbackAction = mock(ReplyCallbackAction.class);
        InteractionHook hook = mock(InteractionHook.class);

        when(event.isFromGuild()).thenReturn(true);
        when(event.getName()).thenReturn(DiscordCommandCatalog.CMD_STOCK);
        when(event.getSubcommandName()).thenReturn(DiscordCommandCatalog.SUB_QUOTE);
        when(event.getGuild()).thenReturn(guild);
        when(event.getJDA()).thenReturn(jda);
        when(event.getUser()).thenReturn(user);
        when(event.getChannel()).thenReturn(channel);
        when(event.getToken()).thenReturn("token-1");
        when(event.getOption(DiscordCommandCatalog.OPT_SYMBOL)).thenReturn(symbolOption);
        when(symbolOption.getAsString()).thenReturn("aapl");
        when(guild.getIdLong()).thenReturn(10L);
        when(user.getIdLong()).thenReturn(20L);
        when(jda.getGuildById(10L)).thenReturn(guild);
        when(channel.getIdLong()).thenReturn(30L);
        when(channel.getId()).thenReturn("30");
        when(guild.getId()).thenReturn("10");
        when(event.deferReply(anyBoolean())).thenReturn(replyCallbackAction);
        when(event.reply(anyString())).thenReturn(replyCallbackAction);
        when(replyCallbackAction.setEphemeral(true)).thenReturn(replyCallbackAction);

        StockCommandEnvelope envelope = new StockCommandEnvelope(
                "command-1",
                1,
                1L,
                "gateway-1",
                new StockCommand.Quote(10L, 20L, List.of("AAPL")),
                "gateway-1"
        );
        when(stockApplicationService.prepareQuote(10L, 20L, "aapl")).thenReturn(envelope);
        when(stockApplicationService.dispatch(envelope)).thenReturn(CompletableFuture.completedFuture(null));

        doAnswer(invocation -> {
            Consumer<InteractionHook> success = invocation.getArgument(0);
            success.accept(hook);
            return null;
        }).when(replyCallbackAction).queue(any(), any());

        listener.onSlashCommandInteraction(event);

        verify(stockApplicationService).prepareQuote(10L, 20L, "aapl");
        verify(stockApplicationService).dispatch(envelope);
        verify(event).deferReply(true);
        verify(pendingInteractionRepository).put(eq("command-1"), any(InteractionResponseContext.class));
    }

    @Test
    void dispatchesStockBuyWithParsedDecimal() {
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
        User user = mock(User.class);
        MessageChannelUnion channel = mock(MessageChannelUnion.class);
        OptionMapping symbolOption = mock(OptionMapping.class);
        OptionMapping amountOption = mock(OptionMapping.class);
        OptionMapping leverageOption = mock(OptionMapping.class);
        ReplyCallbackAction replyCallbackAction = mock(ReplyCallbackAction.class);
        InteractionHook hook = mock(InteractionHook.class);

        when(event.isFromGuild()).thenReturn(true);
        when(event.getName()).thenReturn(DiscordCommandCatalog.CMD_STOCK);
        when(event.getSubcommandName()).thenReturn(DiscordCommandCatalog.SUB_BUY);
        when(event.getGuild()).thenReturn(guild);
        when(event.getJDA()).thenReturn(jda);
        when(event.getUser()).thenReturn(user);
        when(event.getChannel()).thenReturn(channel);
        when(event.getToken()).thenReturn("token-1");
        when(event.getOption(DiscordCommandCatalog.OPT_SYMBOL)).thenReturn(symbolOption);
        when(event.getOption(DiscordCommandCatalog.OPT_AMOUNT)).thenReturn(amountOption);
        when(event.getOption(DiscordCommandCatalog.OPT_LEVERAGE)).thenReturn(leverageOption);
        when(symbolOption.getAsString()).thenReturn("msft");
        when(amountOption.getAsString()).thenReturn("1000.25");
        when(leverageOption.getAsInt()).thenReturn(5);
        when(guild.getIdLong()).thenReturn(10L);
        when(user.getIdLong()).thenReturn(20L);
        when(jda.getGuildById(10L)).thenReturn(guild);
        when(channel.getIdLong()).thenReturn(30L);
        when(channel.getId()).thenReturn("30");
        when(guild.getId()).thenReturn("10");
        when(event.deferReply(anyBoolean())).thenReturn(replyCallbackAction);
        when(event.reply(anyString())).thenReturn(replyCallbackAction);
        when(replyCallbackAction.setEphemeral(true)).thenReturn(replyCallbackAction);

        StockCommandEnvelope envelope = new StockCommandEnvelope(
                "command-2",
                1,
                1L,
                "gateway-1",
                new StockCommand.Buy(10L, 20L, "MSFT", new BigDecimal("1000.25"), 5),
                "gateway-1"
        );
        when(stockApplicationService.prepareBuy(10L, 20L, "msft", new BigDecimal("1000.25"), 5)).thenReturn(envelope);
        when(stockApplicationService.dispatch(envelope)).thenReturn(CompletableFuture.completedFuture(null));

        doAnswer(invocation -> {
            Consumer<InteractionHook> success = invocation.getArgument(0);
            success.accept(hook);
            return null;
        }).when(replyCallbackAction).queue(any(), any());

        listener.onSlashCommandInteraction(event);

        verify(stockApplicationService).prepareBuy(10L, 20L, "msft", new BigDecimal("1000.25"), 5);
        verify(stockApplicationService).dispatch(envelope);
        verify(event).deferReply(false);
        verify(pendingInteractionRepository).put(eq("command-2"), any(InteractionResponseContext.class));
    }

    @Test
    void dispatchesStockListThroughDeferredInteraction() {
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
        User user = mock(User.class);
        MessageChannelUnion channel = mock(MessageChannelUnion.class);
        ReplyCallbackAction replyCallbackAction = mock(ReplyCallbackAction.class);
        InteractionHook hook = mock(InteractionHook.class);

        when(event.isFromGuild()).thenReturn(true);
        when(event.getName()).thenReturn(DiscordCommandCatalog.CMD_STOCK);
        when(event.getSubcommandName()).thenReturn(DiscordCommandCatalog.SUB_LIST);
        when(event.getGuild()).thenReturn(guild);
        when(event.getJDA()).thenReturn(jda);
        when(event.getUser()).thenReturn(user);
        when(event.getChannel()).thenReturn(channel);
        when(event.getToken()).thenReturn("token-1");
        when(guild.getIdLong()).thenReturn(10L);
        when(user.getIdLong()).thenReturn(20L);
        when(jda.getGuildById(10L)).thenReturn(guild);
        when(channel.getIdLong()).thenReturn(30L);
        when(channel.getId()).thenReturn("30");
        when(guild.getId()).thenReturn("10");
        when(event.deferReply(anyBoolean())).thenReturn(replyCallbackAction);
        when(event.reply(anyString())).thenReturn(replyCallbackAction);
        when(replyCallbackAction.setEphemeral(true)).thenReturn(replyCallbackAction);

        StockCommandEnvelope envelope = new StockCommandEnvelope(
                "command-3",
                1,
                1L,
                "gateway-1",
                new StockCommand.ListQuotes(10L, 20L),
                "gateway-1"
        );
        when(stockApplicationService.prepareList(10L, 20L)).thenReturn(envelope);
        when(stockApplicationService.dispatch(envelope)).thenReturn(CompletableFuture.completedFuture(null));

        doAnswer(invocation -> {
            Consumer<InteractionHook> success = invocation.getArgument(0);
            success.accept(hook);
            return null;
        }).when(replyCallbackAction).queue(any(), any());

        listener.onSlashCommandInteraction(event);

        verify(stockApplicationService).prepareList(10L, 20L);
        verify(stockApplicationService).dispatch(envelope);
        verify(event).deferReply(true);
        verify(pendingInteractionRepository).put(eq("command-3"), any(InteractionResponseContext.class));
    }

    @Test
    void dispatchesStockRankAsPublicReply() {
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
        User user = mock(User.class);
        MessageChannelUnion channel = mock(MessageChannelUnion.class);
        OptionMapping periodOption = mock(OptionMapping.class);
        ReplyCallbackAction replyCallbackAction = mock(ReplyCallbackAction.class);
        InteractionHook hook = mock(InteractionHook.class);

        when(event.isFromGuild()).thenReturn(true);
        when(event.getName()).thenReturn(DiscordCommandCatalog.CMD_STOCK);
        when(event.getSubcommandName()).thenReturn(DiscordCommandCatalog.SUB_RANK);
        when(event.getGuild()).thenReturn(guild);
        when(event.getJDA()).thenReturn(jda);
        when(event.getUser()).thenReturn(user);
        when(event.getChannel()).thenReturn(channel);
        when(event.getToken()).thenReturn("token-1");
        when(event.getOption(DiscordCommandCatalog.OPT_PERIOD)).thenReturn(periodOption);
        when(periodOption.getAsString()).thenReturn("day");
        when(guild.getIdLong()).thenReturn(10L);
        when(user.getIdLong()).thenReturn(20L);
        when(jda.getGuildById(10L)).thenReturn(guild);
        when(channel.getIdLong()).thenReturn(30L);
        when(channel.getId()).thenReturn("30");
        when(guild.getId()).thenReturn("10");
        when(event.deferReply(anyBoolean())).thenReturn(replyCallbackAction);
        when(event.reply(anyString())).thenReturn(replyCallbackAction);
        when(replyCallbackAction.setEphemeral(true)).thenReturn(replyCallbackAction);

        StockCommandEnvelope envelope = new StockCommandEnvelope(
                "command-4",
                1,
                1L,
                "gateway-1",
                new StockCommand.Rank(10L, 20L, "day"),
                "gateway-1"
        );
        when(stockApplicationService.prepareRank(10L, 20L, "day")).thenReturn(envelope);
        when(stockApplicationService.dispatch(envelope)).thenReturn(CompletableFuture.completedFuture(null));

        doAnswer(invocation -> {
            Consumer<InteractionHook> success = invocation.getArgument(0);
            success.accept(hook);
            return null;
        }).when(replyCallbackAction).queue(any(), any());

        listener.onSlashCommandInteraction(event);

        verify(stockApplicationService).prepareRank(10L, 20L, "day");
        verify(stockApplicationService).dispatch(envelope);
        verify(event).deferReply(false);
        verify(pendingInteractionRepository).put(eq("command-4"), any(InteractionResponseContext.class));
    }
}
