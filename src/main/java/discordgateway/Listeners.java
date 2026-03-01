package discordgateway;

import discordgateway.audio.GuildMusicManager;
import discordgateway.audio.PlayerManager;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.managers.AudioManager;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * JDA 5.6.1-safe slash command listener
 *
 * Key fixes:
 * - Do NOT call event.getMember().getVoiceState() (detached entity issue)
 * - Use guild.retrieveMemberVoiceStateById(userId)
 * - Guild cache lookup may be null, so fallback to event.getGuild()
 */
public class Listeners extends ListenerAdapter {

    private static final String PIZZA_IMAGE = "https://i.namu.wiki/i/fnE10XPWcq13FQcHrKhGdFBC4gJbwvQIm2uBHVfjR0b5CBEoS7d72f4wCapkmtS6mwQPhDO6L1-VsAfP_nvR0vaedr4eg8mN-eOfPVbR3EGdlIAJhi6qEtgAkbLCZUfjewnMR18sLcz_u_-wsSGrsA.webp";

    // Slash command names
    private static final String CMD_JOIN   = "join";
    private static final String CMD_LEAVE  = "leave";
    private static final String CMD_PLAY   = "play";
    private static final String CMD_STOP   = "stop";
    private static final String CMD_SKIP   = "skip";
    private static final String CMD_QUEUE  = "queue";
    private static final String CMD_CLEAR  = "clear";
    private static final String CMD_PAUSE  = "pause";
    private static final String CMD_RESUME = "resume";
    private static final String CMD_SFX    = "sfx";
    private static final String CMD_PIZZA  = "pizza";

    // Option names
    private static final String OPT_QUERY    = "query";
    private static final String OPT_AUTOPLAY = "autoplay";
    private static final String OPT_SFX_NAME = "name";

    // Autocomplete cache
    private static final Duration AUTOCOMPLETE_TTL = Duration.ofSeconds(30);
    private final ConcurrentHashMap<String, CachedChoices> autoCache = new ConcurrentHashMap<>();

    private record CachedChoices(long createdAtMillis, List<Command.Choice> choices) {}

    @Override
    public void onReady(@NotNull ReadyEvent event) {
        System.out.println("onReady fired!");

        OptionData playQuery = new OptionData(OptionType.STRING, OPT_QUERY, "검색어 또는 URL", true)
                .setAutoComplete(true);

        OptionData playAuto = new OptionData(OptionType.BOOLEAN, OPT_AUTOPLAY, "곡이 끝나면 자동 추천 재생", false);

        OptionData sfxName = new OptionData(OptionType.STRING, OPT_SFX_NAME, "재생할 효과음", true)
                .addChoice("gsuck", "gsuck.mp3")
                .addChoice("smbj", "smbj.mp3");

        var commands = List.of(
                Commands.slash(CMD_JOIN,   "현재 음성 채널로 봇을 입장시킵니다"),
                Commands.slash(CMD_LEAVE,  "봇을 음성 채널에서 퇴장시킵니다"),
                Commands.slash(CMD_PLAY,   "음악을 재생합니다").addOptions(playQuery, playAuto),
                Commands.slash(CMD_STOP,   "재생을 중지하고 큐를 비웁니다"),
                Commands.slash(CMD_SKIP,   "다음 곡으로 건너뜁니다"),
                Commands.slash(CMD_QUEUE,  "현재 대기열을 표시합니다"),
                Commands.slash(CMD_CLEAR,  "대기열을 비웁니다(재생 중인 곡은 유지)"),
                Commands.slash(CMD_PAUSE,  "현재 곡을 일시정지합니다"),
                Commands.slash(CMD_RESUME, "일시정지를 해제합니다"),
                Commands.slash(CMD_SFX,    "로컬 효과음을 재생합니다").addOptions(sfxName),
                Commands.slash(CMD_PIZZA,  "피자 이미지를 출력합니다")
        );

        // Dev guild (fast update) -> fallback global
        String devGuildId = System.getenv("DISCORD_DEV_GUILD_ID");
        if (devGuildId != null && !devGuildId.isBlank()) {
            Guild guild = event.getJDA().getGuildById(devGuildId);
            if (guild != null) {
                guild.updateCommands().addCommands(commands).queue();
                return;
            }
        }

        event.getJDA().updateCommands().addCommands(commands).queue();
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        if (!event.isFromGuild()) {
            event.reply("이 봇은 서버(길드) 채널에서만 동작합니다.").setEphemeral(true).queue();
            return;
        }

        try {
            switch (event.getName()) {
                case CMD_JOIN -> handleJoin(event);
                case CMD_LEAVE -> handleLeave(event);
                case CMD_PLAY -> handlePlay(event);
                case CMD_STOP -> handleStop(event);
                case CMD_SKIP -> handleSkip(event);
                case CMD_QUEUE -> handleQueue(event);
                case CMD_CLEAR -> handleClear(event);
                case CMD_PAUSE -> handlePause(event);
                case CMD_RESUME -> handleResume(event);
                case CMD_SFX -> handleSfx(event);
                case CMD_PIZZA -> handlePizza(event);
                default -> event.reply("알 수 없는 커맨드입니다.").setEphemeral(true).queue();
            }
        } catch (Exception e) {
            if (!event.isAcknowledged()) {
                event.reply("❌ 명령 처리 중 오류가 발생했습니다: " + e.getMessage())
                        .setEphemeral(true)
                        .queue();
            } else {
                event.getHook().editOriginal("❌ 명령 처리 중 오류가 발생했습니다: " + e.getMessage()).queue();
            }
            e.printStackTrace();
        }
    }

    @Override
    public void onCommandAutoCompleteInteraction(@NotNull CommandAutoCompleteInteractionEvent event) {
        if (!event.isFromGuild()) {
            event.replyChoices(Collections.emptyList()).queue();
            return;
        }

        if (!Objects.equals(event.getName(), CMD_PLAY)) return;
        if (!Objects.equals(event.getFocusedOption().getName(), OPT_QUERY)) return;

        String typed = Objects.toString(event.getFocusedOption().getValue(), "").trim();
        if (typed.length() < 3) {
            event.replyChoices(Collections.emptyList()).queue();
            return;
        }

        // Cache hit
        String cacheKey = typed.toLowerCase();
        CachedChoices cached = autoCache.get(cacheKey);
        long now = System.currentTimeMillis();
        if (cached != null && (now - cached.createdAtMillis) <= AUTOCOMPLETE_TTL.toMillis()) {
            event.replyChoices(cached.choices()).queue();
            return;
        }

        // Async search (Discord timeout window)
        AtomicBoolean replied = new AtomicBoolean(false);

        PlayerManager.getINSTANCE()
                .searchYouTubeChoices(typed, 15)
                .orTimeout(2500, TimeUnit.MILLISECONDS)
                .whenComplete((choices, err) -> {
                    if (!replied.compareAndSet(false, true)) return;

                    List<Command.Choice> result;
                    if (err != null || choices == null) {
                        result = Collections.emptyList();
                    } else {
                        List<Command.Choice> sanitized = new ArrayList<>();
                        for (Command.Choice c : choices) {
                            String name = trimToMax(c.getName(), 100);
                            String value = trimToMax(c.getAsString(), 100);
                            sanitized.add(new Command.Choice(name, value));
                            if (sanitized.size() >= 25) break;
                        }
                        result = sanitized;
                        autoCache.put(cacheKey, new CachedChoices(System.currentTimeMillis(), result));
                    }

                    event.replyChoices(result).queue(
                            null,
                            fail -> {
                                // timeout/unknown interaction -> ignore
                            }
                    );
                });
    }

    // =========================
    // Command handlers
    // =========================

    private void handleJoin(SlashCommandInteractionEvent event) {
        Guild guild = requireUsableGuild(event);
        if (guild == null) return;

        event.deferReply(true).queue();

        retrieveInvokerVoiceChannel(guild, event.getUser().getIdLong())
                .whenComplete((audioChannel, err) -> {
                    if (err != null || audioChannel == null) {
                        safeEditOriginal(event, "⚠️ 먼저 음성 채널에 들어가세요!");
                        return;
                    }

                    try {
                        connectBotToChannel(guild, audioChannel);
                        safeEditOriginal(event, "✅ 음성 채널에 입장했습니다.");
                    } catch (Exception e) {
                        safeEditOriginal(event, "❌ 음성 채널 입장 중 오류: " + e.getMessage());
                        e.printStackTrace();
                    }
                });
    }

    private void handleLeave(SlashCommandInteractionEvent event) {
        Guild guild = requireUsableGuild(event);
        if (guild == null) return;

        AudioManager am = guild.getAudioManager();
        if (!am.isConnected()) {
            event.reply("⚠️ 봇이 음성 채널에 들어와 있지 않습니다.").setEphemeral(true).queue();
            return;
        }

        am.closeAudioConnection();
        event.reply("👋 음성 채널에서 퇴장했습니다.").queue();
    }

    private void handlePlay(SlashCommandInteractionEvent event) {
        Guild guild = requireUsableGuild(event);
        if (guild == null) return;

        TextChannel textChannel = requireUsableTextChannel(event, guild);
        if (textChannel == null) return;

        String query = getStringOption(event, OPT_QUERY, "");
        boolean autoPlay = getBoolOption(event, OPT_AUTOPLAY, false);

        if (query.isBlank()) {
            event.reply("❗ 사용법: `/play query:<검색어 또는 URL> autoplay:<true|false>`")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        event.deferReply(true).queue();

        retrieveInvokerVoiceChannel(guild, event.getUser().getIdLong())
                .whenComplete((audioChannel, err) -> {
                    if (err != null || audioChannel == null) {
                        safeEditOriginal(event, "⚠️ 먼저 음성 채널에 들어가세요!");
                        return;
                    }

                    try {
                        connectBotToChannel(guild, audioChannel);

                        String trackUrl = (query.startsWith("http://") || query.startsWith("https://"))
                                ? query
                                : "ytsearch:" + query;

                        GuildMusicManager gm = PlayerManager.getINSTANCE().getMusicManager(guild);
                        gm.scheduler.setAutoPlay(autoPlay);

                        // PlayerManager currently doesn't use member for voice-state access (safe enough to pass through)
                        Member interactionMember = event.getMember();
                        PlayerManager.getINSTANCE().loadAndPlay(textChannel, trackUrl, interactionMember);

                        safeEditOriginal(event, "✅ 재생 요청을 처리했습니다.");
                    } catch (Exception e) {
                        safeEditOriginal(event, "❌ 재생 요청 처리 중 오류: " + e.getMessage());
                        e.printStackTrace();
                    }
                });
    }

    private void handleStop(SlashCommandInteractionEvent event) {
        Guild guild = requireUsableGuild(event);
        if (guild == null) return;

        GuildMusicManager gm = PlayerManager.getINSTANCE().getMusicManager(guild);
        gm.audioPlayer.stopTrack();
        gm.scheduler.clearQueue();

        event.reply("⏹️ 재생을 중지하고 큐를 비웠습니다.").setEphemeral(true).queue();
    }

    private void handleSkip(SlashCommandInteractionEvent event) {
        Guild guild = requireUsableGuild(event);
        if (guild == null) return;

        GuildMusicManager gm = PlayerManager.getINSTANCE().getMusicManager(guild);
        gm.scheduler.nextTrack();

        event.reply("⏭️ 다음 곡으로 건너뜁니다.").setEphemeral(true).queue();
    }

    private void handleQueue(SlashCommandInteractionEvent event) {
        Guild guild = requireUsableGuild(event);
        if (guild == null) return;

        GuildMusicManager gm = PlayerManager.getINSTANCE().getMusicManager(guild);
        List<String> list = gm.scheduler.showList();

        if (list.isEmpty()) {
            event.reply("📭 현재 대기열이 비어 있습니다.").setEphemeral(true).queue();
            return;
        }

        String content = String.join("\n", list.stream().limit(30).toList());
        event.reply("🎶 현재 대기열:\n" + content).setEphemeral(true).queue();
    }

    private void handleClear(SlashCommandInteractionEvent event) {
        Guild guild = requireUsableGuild(event);
        if (guild == null) return;

        GuildMusicManager gm = PlayerManager.getINSTANCE().getMusicManager(guild);
        gm.scheduler.clearQueue();

        event.reply("🧹 대기열을 비웠습니다.").queue();
    }

    private void handlePause(SlashCommandInteractionEvent event) {
        Guild guild = requireUsableGuild(event);
        if (guild == null) return;

        GuildMusicManager gm = PlayerManager.getINSTANCE().getMusicManager(guild);

        if (gm.audioPlayer.getPlayingTrack() == null) {
            event.reply("⏸️ 재생 중인 곡이 없습니다.").setEphemeral(true).setEphemeral(true).queue();
        } else if (gm.audioPlayer.isPaused()) {
            event.reply("⚠️ 이미 일시 정지 상태입니다.").setEphemeral(true).setEphemeral(true).queue();
        } else {
            gm.audioPlayer.setPaused(true);
            event.reply("⏸️ 곡을 일시 정지했습니다.").setEphemeral(true).queue();
        }
    }

    private void handleResume(SlashCommandInteractionEvent event) {
        Guild guild = requireUsableGuild(event);
        if (guild == null) return;

        GuildMusicManager gm = PlayerManager.getINSTANCE().getMusicManager(guild);

        if (gm.audioPlayer.getPlayingTrack() == null) {
            event.reply("▶️ 재생할 곡이 없습니다.").setEphemeral(true).setEphemeral(true).queue();
        } else if (!gm.audioPlayer.isPaused()) {
            event.reply("⚠️ 현재 재생 중입니다.").setEphemeral(true).setEphemeral(true).queue();
        } else {
            gm.audioPlayer.setPaused(false);
            event.reply("▶️ 재생을 재개했습니다.").setEphemeral(true).queue();
        }
    }

    private void handleSfx(SlashCommandInteractionEvent event) {
        Guild guild = requireUsableGuild(event);
        if (guild == null) return;

        TextChannel textChannel = requireUsableTextChannel(event, guild);
        if (textChannel == null) return;

        String file = getStringOption(event, OPT_SFX_NAME, "");
        if (file.isBlank()) {
            event.reply("효과음 이름이 비어 있습니다.").setEphemeral(true).setEphemeral(true).queue();
            return;
        }

        event.deferReply(true).queue();

        retrieveInvokerVoiceChannel(guild, event.getUser().getIdLong())
                .whenComplete((audioChannel, err) -> {
                    if (err != null || audioChannel == null) {
                        safeEditOriginal(event, "⚠️ 먼저 음성 채널에 들어가세요!");
                        return;
                    }

                    try {
                        connectBotToChannel(guild, audioChannel);

                        String localPath = "resources/" + file; // adjust if needed
                        Member interactionMember = event.getMember();
                        PlayerManager.getINSTANCE().loadAndPlay(textChannel, localPath, interactionMember);

                        safeEditOriginal(event, "🔊 효과음을 재생합니다: `" + file + "`");
                    } catch (Exception e) {
                        safeEditOriginal(event, "❌ 효과음 재생 중 오류: " + e.getMessage());
                        e.printStackTrace();
                    }
                });
    }

    private void handlePizza(SlashCommandInteractionEvent event) {
        event.replyEmbeds(
                new EmbedBuilder()
                        .setTitle("Edou")
                        .setImage(PIZZA_IMAGE)
                        .build()
        ).queue();
    }

    // =========================
    // Helpers
    // =========================

    /**
     * Guild cache may be null in some situations.
     * Try cache first, then fallback to event.getGuild().
     */
    private Guild requireUsableGuild(SlashCommandInteractionEvent event) {
        if (!event.isFromGuild() || event.getGuild() == null) {
            event.reply("이 명령어는 서버에서만 사용할 수 있습니다.")
                    .setEphemeral(true)
                    .queue();
            return null;
        }

        Guild cached = event.getJDA().getGuildById(event.getGuild().getIdLong());
        return (cached != null) ? cached : event.getGuild();
    }

    /**
     * Get a TextChannel safely.
     * Try JDA cache first, then fallback to interaction channel cast.
     */
    private TextChannel requireUsableTextChannel(SlashCommandInteractionEvent event, Guild guild) {
        if (event.getChannelType() != ChannelType.TEXT) {
            event.reply("이 명령어는 일반 텍스트 채널에서만 사용할 수 있습니다.")
                    .setEphemeral(true)
                    .queue();
            return null;
        }

        // 1) Try global JDA cache
        TextChannel tc = event.getJDA().getTextChannelById(event.getChannelIdLong());
        if (tc != null) return tc;

        // 2) Try guild cache (may still be null depending on cache state)
        try {
            tc = guild.getTextChannelById(event.getChannelIdLong());
            if (tc != null) return tc;
        } catch (Exception ignored) {
            // ignore and try interaction channel fallback below
        }

        // 3) Fallback to interaction channel object
        try {
            return event.getChannel().asTextChannel();
        } catch (Exception e) {
            event.reply("❌ 텍스트 채널 정보를 가져오지 못했습니다.")
                    .setEphemeral(true)
                    .queue();
            return null;
        }
    }

    /**
     * JDA 5.6.1-safe voice-state lookup for slash commands (avoids detached member issue)
     */
    private CompletableFuture<AudioChannel> retrieveInvokerVoiceChannel(Guild guild, long userId) {
        CompletableFuture<AudioChannel> future = new CompletableFuture<>();

        guild.retrieveMemberVoiceStateById(userId).queue(
                voiceState -> {
                    if (voiceState == null || !voiceState.inAudioChannel()) {
                        future.completeExceptionally(new IllegalStateException("User is not in a voice channel"));
                        return;
                    }

                    AudioChannel channel = voiceState.getChannel();
                    if (channel == null) {
                        future.completeExceptionally(new IllegalStateException("Voice channel not found"));
                        return;
                    }

                    future.complete(channel);
                },
                future::completeExceptionally
        );

        return future;
    }

    /**
     * Simple and safe: openAudioConnection connects or moves bot.
     * (No selfMember voice-state check -> avoids detached issues)
     */
    private void connectBotToChannel(Guild guild, AudioChannel target) {
        guild.getAudioManager().openAudioConnection(target);
    }

    private void safeEditOriginal(SlashCommandInteractionEvent event, String message) {
        if (event.isAcknowledged()) {
            event.getHook().editOriginal(message).queue(
                    null,
                    err -> {
                        // ignore timeout/deleted interaction/etc
                    }
            );
        } else {
            event.reply(message).setEphemeral(true).queue(
                    null,
                    err -> {
                        // ignore reply failure
                    }
            );
        }
    }

    private String getStringOption(SlashCommandInteractionEvent event, String name, String def) {
        OptionMapping opt = event.getOption(name);
        return opt != null ? opt.getAsString() : def;
    }

    private boolean getBoolOption(SlashCommandInteractionEvent event, String name, boolean def) {
        OptionMapping opt = event.getOption(name);
        return opt != null ? opt.getAsBoolean() : def;
    }

    private String trimToMax(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}