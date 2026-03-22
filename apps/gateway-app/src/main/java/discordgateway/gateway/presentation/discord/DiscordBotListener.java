package discordgateway.gateway.presentation.discord;

import discordgateway.common.command.CommandResult;
import discordgateway.gateway.application.MusicApplicationService;
import discordgateway.gateway.application.PlayAutocompleteService;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public class DiscordBotListener extends ListenerAdapter {

    private static final Logger log = LoggerFactory.getLogger(DiscordBotListener.class);

    private final MusicApplicationService musicApplicationService;
    private final PlayAutocompleteService playAutocompleteService;

    public DiscordBotListener(
            MusicApplicationService musicApplicationService,
            PlayAutocompleteService playAutocompleteService
    ) {
        this.musicApplicationService = musicApplicationService;
        this.playAutocompleteService = playAutocompleteService;
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        if (!event.isFromGuild()) {
            event.reply("이 명령은 서버 텍스트 채널에서만 사용할 수 있습니다.")
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
                default -> event.reply("알 수 없는 명령입니다.").setEphemeral(true).queue();
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

    private void handleLeave(SlashCommandInteractionEvent event) {
        Guild guild = requireUsableGuild(event);
        if (guild == null) {
            return;
        }
        reply(event, musicApplicationService.leave(guild));
    }

    private void handleStop(SlashCommandInteractionEvent event) {
        Guild guild = requireUsableGuild(event);
        if (guild == null) {
            return;
        }
        reply(event, musicApplicationService.stop(guild));
    }

    private void handleSkip(SlashCommandInteractionEvent event) {
        Guild guild = requireUsableGuild(event);
        if (guild == null) {
            return;
        }
        reply(event, musicApplicationService.skip(guild));
    }

    private void handleQueue(SlashCommandInteractionEvent event) {
        Guild guild = requireUsableGuild(event);
        if (guild == null) {
            return;
        }
        reply(event, musicApplicationService.queue(guild));
    }

    private void handleClear(SlashCommandInteractionEvent event) {
        Guild guild = requireUsableGuild(event);
        if (guild == null) {
            return;
        }
        reply(event, musicApplicationService.clear(guild));
    }

    private void handlePause(SlashCommandInteractionEvent event) {
        Guild guild = requireUsableGuild(event);
        if (guild == null) {
            return;
        }
        reply(event, musicApplicationService.pause(guild));
    }

    private void handleResume(SlashCommandInteractionEvent event) {
        Guild guild = requireUsableGuild(event);
        if (guild == null) {
            return;
        }
        reply(event, musicApplicationService.resume(guild));
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

        event.deferReply(true).queue();
        musicApplicationService.join(guild, textChannel, event.getUser().getIdLong())
                .whenComplete((result, err) -> {
                    if (err != null) {
                        safeEditOriginal(event, "먼저 음성 채널에 들어가 주세요.");
                        return;
                    }
                    safeEditOriginal(event, result.message());
                });
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

        event.deferReply(true).queue();
        musicApplicationService.play(guild, textChannel, event.getUser().getIdLong(), query, autoPlay)
                .whenComplete((result, err) -> {
                    if (err != null) {
                        safeEditOriginal(event, "재생 요청 처리 중 오류가 발생했습니다: " + err.getMessage());
                        return;
                    }
                    safeEditOriginal(event, result.message());
                });
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

        event.deferReply(true).queue();
        musicApplicationService.playSfx(guild, textChannel, event.getUser().getIdLong(), file)
                .whenComplete((result, err) -> {
                    if (err != null) {
                        safeEditOriginal(event, "효과음 재생 중 오류가 발생했습니다: " + err.getMessage());
                        return;
                    }
                    safeEditOriginal(event, result.message());
                });
    }

    private void handlePizza(SlashCommandInteractionEvent event) {
        event.replyEmbeds(
                new EmbedBuilder()
                        .setTitle("Edou")
                        .setImage(DiscordCommandCatalog.PIZZA_IMAGE)
                        .build()
        ).queue();
    }

    private void reply(SlashCommandInteractionEvent event, CommandResult result) {
        if (result == null) {
            event.reply("결과가 없습니다.").setEphemeral(true).queue();
            return;
        }

        if (result.ephemeral()) {
            event.reply(result.message()).setEphemeral(true).queue();
        } else {
            event.reply(result.message()).queue();
        }
    }

    private Guild requireUsableGuild(SlashCommandInteractionEvent event) {
        if (!event.isFromGuild() || event.getGuild() == null) {
            event.reply("이 명령은 서버에서만 사용할 수 있습니다.")
                    .setEphemeral(true)
                    .queue();
            return null;
        }

        Guild cached = event.getJDA().getGuildById(event.getGuild().getIdLong());
        return cached != null ? cached : event.getGuild();
    }

    private TextChannel requireUsableTextChannel(SlashCommandInteractionEvent event, Guild guild) {
        if (event.getChannelType() != ChannelType.TEXT) {
            event.reply("이 명령은 일반 텍스트 채널에서만 사용할 수 있습니다.")
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
            event.reply("텍스트 채널 정보를 가져오지 못했습니다.")
                    .setEphemeral(true)
                    .queue();
            return null;
        }
    }

    private void safeEditOriginal(SlashCommandInteractionEvent event, String message) {
        if (event.isAcknowledged()) {
            event.getHook().editOriginal(message).queue(
                    null,
                    failure -> log.warn(
                            "Failed to edit original interaction response. command={} guild={} channel={}",
                            event.getName(),
                            event.getGuild() != null ? event.getGuild().getId() : "unknown",
                            event.getChannel().getId(),
                            failure
                    )
            );
        } else {
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
    }

    private String getStringOption(SlashCommandInteractionEvent event, String name, String def) {
        OptionMapping optionMapping = event.getOption(name);
        return optionMapping != null ? optionMapping.getAsString() : def;
    }

    private boolean getBoolOption(SlashCommandInteractionEvent event, String name, boolean def) {
        OptionMapping optionMapping = event.getOption(name);
        return optionMapping != null ? optionMapping.getAsBoolean() : def;
    }
}
