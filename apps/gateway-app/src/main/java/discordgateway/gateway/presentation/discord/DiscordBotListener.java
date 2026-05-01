package discordgateway.gateway.presentation.discord;

import discordgateway.common.command.MusicCommandEnvelope;
import discordgateway.gateway.application.MusicApplicationService;
import discordgateway.gateway.application.PlayAutocompleteService;
import discordgateway.gateway.application.StockApplicationService;
import discordgateway.gateway.interaction.InteractionResponseContext;
import discordgateway.gateway.interaction.PendingInteractionRepository;
import discordgateway.stock.command.StockCommandEnvelope;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Collections;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public class DiscordBotListener extends ListenerAdapter {

    private static final Logger log = LoggerFactory.getLogger(DiscordBotListener.class);
    private static final Duration PENDING_INTERACTION_TTL = Duration.ofMinutes(15);

    private final MusicApplicationService musicApplicationService;
    private final StockApplicationService stockApplicationService;
    private final PlayAutocompleteService playAutocompleteService;
    private final PendingInteractionRepository pendingInteractionRepository;

    public DiscordBotListener(
            MusicApplicationService musicApplicationService,
            StockApplicationService stockApplicationService,
            PlayAutocompleteService playAutocompleteService,
            PendingInteractionRepository pendingInteractionRepository
    ) {
        this.musicApplicationService = musicApplicationService;
        this.stockApplicationService = stockApplicationService;
        this.playAutocompleteService = playAutocompleteService;
        this.pendingInteractionRepository = pendingInteractionRepository;
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        if (!event.isFromGuild()) {
            event.reply("이 명령어는 서버 텍스트 채널에서만 사용할 수 있습니다.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        try {
            switch (event.getName()) {
                case DiscordCommandCatalog.CMD_JOIN -> handleJoin(event);
                case DiscordCommandCatalog.CMD_LEAVE -> handleLeave(event);
                case DiscordCommandCatalog.CMD_PLAY -> handlePlay(event);
                case DiscordCommandCatalog.CMD_STOP -> handleStop(event);
                case DiscordCommandCatalog.CMD_SKIP -> handleSkip(event);
                case DiscordCommandCatalog.CMD_QUEUE -> handleQueue(event);
                case DiscordCommandCatalog.CMD_CLEAR -> handleClear(event);
                case DiscordCommandCatalog.CMD_PAUSE -> handlePause(event);
                case DiscordCommandCatalog.CMD_RESUME -> handleResume(event);
                case DiscordCommandCatalog.CMD_SFX -> handleSfx(event);
                case DiscordCommandCatalog.CMD_PIZZA -> handlePizza(event);
                case DiscordCommandCatalog.CMD_STOCK -> handleStock(event);
                default -> event.reply("알 수 없는 명령어입니다.").setEphemeral(true).queue();
            }
        } catch (Exception e) {
            if (!event.isAcknowledged()) {
                event.reply("명령 처리 중 오류가 발생했습니다: " + e.getMessage())
                        .setEphemeral(true)
                        .queue();
            } else {
                safeEditOriginal(event, "명령 처리 중 오류가 발생했습니다: " + e.getMessage());
            }
            log.error("Slash command handling failed. command={}", event.getName(), e);
        }
    }

    @Override
    public void onCommandAutoCompleteInteraction(@NotNull CommandAutoCompleteInteractionEvent event) {
        if (!event.isFromGuild()) {
            event.replyChoices(Collections.emptyList()).queue();
            return;
        }

        if (!Objects.equals(event.getName(), DiscordCommandCatalog.CMD_PLAY)) {
            return;
        }
        if (!Objects.equals(event.getFocusedOption().getName(), DiscordCommandCatalog.OPT_QUERY)) {
            return;
        }

        AtomicBoolean replied = new AtomicBoolean(false);
        playAutocompleteService.complete(event.getFocusedOption().getValue())
                .whenComplete((choices, err) -> {
                    if (!replied.compareAndSet(false, true)) {
                        return;
                    }
                    event.replyChoices(err == null && choices != null ? choices : Collections.emptyList()).queue(
                            null,
                            fail -> {
                                // ignore timeout / unknown interaction
                            }
                    );
                });
    }

    private void handleJoin(SlashCommandInteractionEvent event) {
        Guild guild = requireUsableGuild(event);
        if (guild == null) {
            return;
        }

        TextChannel textChannel = requireUsableTextChannel(event, guild);
        if (textChannel == null) {
            return;
        }

        dispatchDeferred(
                event,
                musicApplicationService.prepareJoin(guild, textChannel, event.getUser().getIdLong())
        );
    }

    private void handleLeave(SlashCommandInteractionEvent event) {
        Guild guild = requireUsableGuild(event);
        if (guild == null) {
            return;
        }

        dispatchDeferred(event, musicApplicationService.prepareLeave(guild));
    }

    private void handlePlay(SlashCommandInteractionEvent event) {
        Guild guild = requireUsableGuild(event);
        if (guild == null) {
            return;
        }

        TextChannel textChannel = requireUsableTextChannel(event, guild);
        if (textChannel == null) {
            return;
        }

        String query = getStringOption(event, DiscordCommandCatalog.OPT_QUERY, "");
        boolean autoPlay = getBoolOption(event, DiscordCommandCatalog.OPT_AUTOPLAY, false);

        dispatchDeferred(
                event,
                musicApplicationService.preparePlay(guild, textChannel, event.getUser().getIdLong(), query, autoPlay)
        );
    }

    private void handleStop(SlashCommandInteractionEvent event) {
        Guild guild = requireUsableGuild(event);
        if (guild == null) {
            return;
        }

        dispatchDeferred(event, musicApplicationService.prepareStop(guild));
    }

    private void handleSkip(SlashCommandInteractionEvent event) {
        Guild guild = requireUsableGuild(event);
        if (guild == null) {
            return;
        }

        dispatchDeferred(event, musicApplicationService.prepareSkip(guild));
    }

    private void handleQueue(SlashCommandInteractionEvent event) {
        Guild guild = requireUsableGuild(event);
        if (guild == null) {
            return;
        }

        dispatchDeferred(event, musicApplicationService.prepareQueue(guild));
    }

    private void handleClear(SlashCommandInteractionEvent event) {
        Guild guild = requireUsableGuild(event);
        if (guild == null) {
            return;
        }

        dispatchDeferred(event, musicApplicationService.prepareClear(guild));
    }

    private void handlePause(SlashCommandInteractionEvent event) {
        Guild guild = requireUsableGuild(event);
        if (guild == null) {
            return;
        }

        dispatchDeferred(event, musicApplicationService.preparePause(guild));
    }

    private void handleResume(SlashCommandInteractionEvent event) {
        Guild guild = requireUsableGuild(event);
        if (guild == null) {
            return;
        }

        dispatchDeferred(event, musicApplicationService.prepareResume(guild));
    }

    private void handleSfx(SlashCommandInteractionEvent event) {
        Guild guild = requireUsableGuild(event);
        if (guild == null) {
            return;
        }

        TextChannel textChannel = requireUsableTextChannel(event, guild);
        if (textChannel == null) {
            return;
        }

        String file = getStringOption(event, DiscordCommandCatalog.OPT_SFX_NAME, "");

        dispatchDeferred(
                event,
                musicApplicationService.preparePlaySfx(guild, textChannel, event.getUser().getIdLong(), file)
        );
    }

    private void handlePizza(SlashCommandInteractionEvent event) {
        event.replyEmbeds(
                new EmbedBuilder()
                        .setTitle("Edou")
                        .setImage(DiscordCommandCatalog.PIZZA_IMAGE)
                        .build()
        ).queue();
    }

    private void handleStock(SlashCommandInteractionEvent event) {
        Guild guild = requireUsableGuild(event);
        if (guild == null) {
            return;
        }

        String subcommand = event.getSubcommandName();
        if (subcommand == null || subcommand.isBlank()) {
            event.reply("주식 명령어 종류를 선택해 주세요.").setEphemeral(true).queue();
            return;
        }

        long guildId = guild.getIdLong();
        long requesterId = event.getUser().getIdLong();

        StockCommandEnvelope envelope = switch (subcommand) {
            case DiscordCommandCatalog.SUB_QUOTE -> stockApplicationService.prepareQuote(
                    guildId,
                    requesterId,
                    getRequiredStringOption(event, DiscordCommandCatalog.OPT_SYMBOL)
            );
            case DiscordCommandCatalog.SUB_LIST -> stockApplicationService.prepareList(guildId, requesterId);
            case DiscordCommandCatalog.SUB_BUY -> stockApplicationService.prepareBuy(
                    guildId,
                    requesterId,
                    getRequiredStringOption(event, DiscordCommandCatalog.OPT_SYMBOL),
                    parseDecimalOption(event, DiscordCommandCatalog.OPT_AMOUNT),
                    getIntegerOption(event, DiscordCommandCatalog.OPT_LEVERAGE)
            );
            case DiscordCommandCatalog.SUB_SELL -> stockApplicationService.prepareSell(
                    guildId,
                    requesterId,
                    getRequiredStringOption(event, DiscordCommandCatalog.OPT_SYMBOL),
                    parseDecimalOption(event, DiscordCommandCatalog.OPT_QUANTITY)
            );
            case DiscordCommandCatalog.SUB_BALANCE -> stockApplicationService.prepareBalance(guildId, requesterId);
            case DiscordCommandCatalog.SUB_PORTFOLIO -> stockApplicationService.preparePortfolio(guildId, requesterId);
            case DiscordCommandCatalog.SUB_HISTORY -> stockApplicationService.prepareHistory(
                    guildId,
                    requesterId,
                    getIntegerOption(event, DiscordCommandCatalog.OPT_LIMIT)
            );
            case DiscordCommandCatalog.SUB_RANK -> stockApplicationService.prepareRank(
                    guildId,
                    requesterId,
                    getRequiredStringOption(event, DiscordCommandCatalog.OPT_PERIOD)
            );
            default -> throw new IllegalArgumentException("Unknown stock subcommand: " + subcommand);
        };

        dispatchDeferred(
                event,
                envelope.commandId(),
                !isPublicStockSubcommand(subcommand),
                () -> stockApplicationService.dispatch(envelope)
        );
    }

    private void dispatchDeferred(SlashCommandInteractionEvent event, MusicCommandEnvelope envelope) {
        dispatchDeferred(event, envelope.message().commandId(), () -> musicApplicationService.dispatch(envelope));
    }

    private void dispatchDeferred(
            SlashCommandInteractionEvent event,
            String commandId,
            Supplier<CompletableFuture<?>> dispatchFutureSupplier
    ) {
        dispatchDeferred(event, commandId, true, dispatchFutureSupplier);
    }

    private void dispatchDeferred(
            SlashCommandInteractionEvent event,
            String commandId,
            boolean ephemeral,
            Supplier<CompletableFuture<?>> dispatchFutureSupplier
    ) {
        event.deferReply(ephemeral).queue(
                hook -> {
                    long now = System.currentTimeMillis();
                    try {
                        pendingInteractionRepository.put(
                                commandId,
                                new InteractionResponseContext(
                                        event.getToken(),
                                        event.getName(),
                                        event.getGuild() != null ? event.getGuild().getIdLong() : 0L,
                                        event.getChannel().getIdLong(),
                                        now,
                                        now + PENDING_INTERACTION_TTL.toMillis()
                                )
                        );
                    } catch (Exception err) {
                        safeEditHook(
                                hook,
                                "명령 전송에 실패했습니다: " + err.getMessage(),
                                event.getName(),
                                event.getGuild() != null ? event.getGuild().getId() : "unknown",
                                event.getChannel().getId()
                        );
                        return;
                    }

                    CompletableFuture<?> dispatchFuture;
                    try {
                        dispatchFuture = dispatchFutureSupplier.get();
                    } catch (Exception err) {
                        pendingInteractionRepository.remove(commandId);
                        safeEditHook(
                                hook,
                                "명령 전송에 실패했습니다: " + err.getMessage(),
                                event.getName(),
                                event.getGuild() != null ? event.getGuild().getId() : "unknown",
                                event.getChannel().getId()
                        );
                        return;
                    }

                    dispatchFuture.whenComplete((ignored, err) -> {
                        if (err == null) {
                            return;
                        }
                        pendingInteractionRepository.remove(commandId);
                        safeEditHook(
                                hook,
                                "명령 전송에 실패했습니다: " + err.getMessage(),
                                event.getName(),
                                event.getGuild() != null ? event.getGuild().getId() : "unknown",
                                event.getChannel().getId()
                        );
                    });
                },
                failure -> log.warn(
                        "Failed to defer interaction. command={} guild={} channel={}",
                        event.getName(),
                        event.getGuild() != null ? event.getGuild().getId() : "unknown",
                        event.getChannel().getId(),
                        failure
                )
        );
    }

    private boolean isPublicStockSubcommand(String subcommand) {
        return switch (subcommand) {
            case DiscordCommandCatalog.SUB_BUY,
                 DiscordCommandCatalog.SUB_SELL,
                 DiscordCommandCatalog.SUB_RANK -> true;
            default -> false;
        };
    }

    private Guild requireUsableGuild(SlashCommandInteractionEvent event) {
        if (!event.isFromGuild() || event.getGuild() == null) {
            event.reply("이 명령어는 서버에서만 사용할 수 있습니다.")
                    .setEphemeral(true)
                    .queue();
            return null;
        }

        Guild cached = event.getJDA().getGuildById(event.getGuild().getIdLong());
        return cached != null ? cached : event.getGuild();
    }

    private TextChannel requireUsableTextChannel(SlashCommandInteractionEvent event, Guild guild) {
        if (event.getChannelType() != ChannelType.TEXT) {
            event.reply("이 명령어는 텍스트 채널에서만 사용할 수 있습니다.")
                    .setEphemeral(true)
                    .queue();
            return null;
        }

        TextChannel textChannel = event.getJDA().getTextChannelById(event.getChannelIdLong());
        if (textChannel != null) {
            return textChannel;
        }

        try {
            textChannel = guild.getTextChannelById(event.getChannelIdLong());
            if (textChannel != null) {
                return textChannel;
            }
        } catch (Exception ignored) {
        }

        try {
            return event.getChannel().asTextChannel();
        } catch (Exception e) {
            event.reply("텍스트 채널 정보를 확인하지 못했습니다.")
                    .setEphemeral(true)
                    .queue();
            return null;
        }
    }

    private void safeEditOriginal(SlashCommandInteractionEvent event, String message) {
        if (event.isAcknowledged()) {
            safeEditHook(
                    event.getHook(),
                    message,
                    event.getName(),
                    event.getGuild() != null ? event.getGuild().getId() : "unknown",
                    event.getChannel().getId()
            );
            return;
        }

        event.reply(message).setEphemeral(true).queue(
                null,
                failure -> log.warn(
                        "Failed to send interaction response. command={} guild={} channel={}",
                        event.getName(),
                        event.getGuild() != null ? event.getGuild().getId() : "unknown",
                        event.getChannel().getId(),
                        failure
                )
        );
    }

    private void safeEditHook(
            InteractionHook hook,
            String message,
            String commandName,
            String guildId,
            String channelId
    ) {
        hook.editOriginal(message).queue(
                null,
                failure -> log.warn(
                        "Failed to edit original interaction response. command={} guild={} channel={}",
                        commandName,
                        guildId,
                        channelId,
                        failure
                )
        );
    }

    private String getStringOption(SlashCommandInteractionEvent event, String name, String def) {
        OptionMapping optionMapping = event.getOption(name);
        return optionMapping != null ? optionMapping.getAsString() : def;
    }

    private boolean getBoolOption(SlashCommandInteractionEvent event, String name, boolean def) {
        OptionMapping optionMapping = event.getOption(name);
        return optionMapping != null ? optionMapping.getAsBoolean() : def;
    }

    private String getRequiredStringOption(SlashCommandInteractionEvent event, String name) {
        OptionMapping optionMapping = event.getOption(name);
        if (optionMapping == null) {
            throw new IllegalArgumentException("Missing option: " + name);
        }
        String value = optionMapping.getAsString();
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Blank option: " + name);
        }
        return value.trim();
    }

    private Integer getIntegerOption(SlashCommandInteractionEvent event, String name) {
        OptionMapping optionMapping = event.getOption(name);
        return optionMapping != null ? optionMapping.getAsInt() : null;
    }

    private BigDecimal parseDecimalOption(SlashCommandInteractionEvent event, String name) {
        String raw = getRequiredStringOption(event, name);
        try {
            return new BigDecimal(raw);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    String.format(Locale.ROOT, "%s 값은 숫자로 입력해 주세요: %s", name, raw),
                    exception
            );
        }
    }
}
