package discordgateway;

import discordgateway.audio.GuildMusicManager;
import discordgateway.audio.PlayerManager;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Slash command support for JDA (gateway interactions).
 *
 * Requirement:
 * - If you previously set an Interactions Endpoint URL (webhook interactions),
 *   you must unset it to receive interaction events via gateway in JDA.
 */
public class Listeners extends ListenerAdapter {

    private static final String PIZZA_IMAGE = "https://images.unsplash.com/photo-1548365328-9f547fb0953d";

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

    // Autocomplete cache: typedText(lower) -> cached choices
    private static final Duration AUTOCOMPLETE_TTL = Duration.ofSeconds(30);
    private final ConcurrentHashMap<String, CachedChoices> autoCache = new ConcurrentHashMap<>();

    private record CachedChoices(long createdAtMillis, List<Command.Choice> choices) {}

    @Override
    public void onReady(@NotNull ReadyEvent event) {
        System.out.println("onReady fired!");
        // Build command definitions (option UI shown while typing in Discord client)
        OptionData playQuery = new OptionData(OptionType.STRING, OPT_QUERY, "검색어 또는 URL", true)
                .setAutoComplete(true); // enables autocomplete interactions
        OptionData playAuto = new OptionData(OptionType.BOOLEAN, OPT_AUTOPLAY, "곡이 끝나면 자동 추천 재생", false);

        OptionData sfxName = new OptionData(OptionType.STRING, OPT_SFX_NAME, "재생할 효과음", true)
                // fixed small set -> use choices (not autocomplete)
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

        // Dev guild 등록(즉시 반영) vs Global 등록(최대 1시간 전파 가능)
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
        // Basic safety
        if (!event.isFromGuild()) {
            event.reply("이 봇은 서버(길드) 채널에서만 동작합니다.").setEphemeral(true).queue();
            return;
        }

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
    }

    @Override
    public void onCommandAutoCompleteInteraction(@NotNull CommandAutoCompleteInteractionEvent event) {
        if (!event.isFromGuild()) {
            event.replyChoices(Collections.emptyList()).queue();
            return;
        }
        if (!Objects.equals(event.getName(), CMD_PLAY)) {
            return;
        }
        if (!Objects.equals(event.getFocusedOption().getName(), OPT_QUERY)) {
            return;
        }

        String typed = Objects.toString(event.getFocusedOption().getValue(), "").trim();
        if (typed.length() < 3) {
            event.replyChoices(Collections.emptyList()).queue();
            return;
        }

        // 1) Fast path: cache hit
        String cacheKey = typed.toLowerCase();
        CachedChoices cached = autoCache.get(cacheKey);
        long now = System.currentTimeMillis();
        if (cached != null && (now - cached.createdAtMillis) <= AUTOCOMPLETE_TTL.toMillis()) {
            event.replyChoices(cached.choices()).queue();
            return;
        }

        // 2) Slow path: run a YouTube search through PlayerManager (LavaPlayer "ytsearch:")
        //    Must respond quickly; do NOT defer. (Most libraries warn about 3s window.)
        AtomicBoolean replied = new AtomicBoolean(false);

        PlayerManager.getINSTANCE()
                .searchYouTubeChoices(typed, 15) // keep it small for speed
                .orTimeout(2500, TimeUnit.MILLISECONDS) // leave time budget
                .whenComplete((choices, err) -> {
                    if (!replied.compareAndSet(false, true)) return;

                    List<Command.Choice> result;
                    if (err != null || choices == null) {
                        result = Collections.emptyList();
                    } else {
                        result = choices;
                        autoCache.put(cacheKey, new CachedChoices(System.currentTimeMillis(), result));
                    }
                    event.replyChoices(result).queue();
                });
    }

    private void handleJoin(SlashCommandInteractionEvent event) {
        Member member = event.getMember();
        Guild guild = event.getGuild();
        if (member == null || guild == null) {
            event.reply("멤버/길드 정보를 가져오지 못했습니다.").setEphemeral(true).queue();
            return;
        }

        String err = ensureConnectedToMemberVoice(guild, member);
        if (err != null) {
            event.reply(err).setEphemeral(true).queue();
        } else {
            event.reply("✅ 음성 채널에 입장했습니다.").queue();
        }
    }

    private void handleLeave(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        if (guild == null) {
            event.reply("길드 정보를 가져오지 못했습니다.").setEphemeral(true).queue();
            return;
        }

        AudioManager am = guild.getAudioManager();
        if (!am.isConnected()) {
            event.reply("⚠️ 봇이 음성 채널에 들어와 있지 않습니다.").setEphemeral(true).queue();
            return;
        }
        am.closeAudioConnection();
        event.reply("👋 음성 채널에서 퇴장했습니다.").queue();
    }

    private void handlePlay(SlashCommandInteractionEvent event) {
        Member member = event.getMember();
        Guild guild = event.getGuild();
        TextChannel textChannel = event.getChannel().asTextChannel(); // slash in guild text channel

        if (member == null || guild == null) {
            event.reply("멤버/길드 정보를 가져오지 못했습니다.").setEphemeral(true).queue();
            return;
        }

        String query = getStringOption(event, OPT_QUERY, "");
        boolean autoPlay = getBoolOption(event, OPT_AUTOPLAY, false);

        if (query.isBlank()) {
            event.reply("❗ 사용법: `/play query:<검색어 또는 URL> autoplay:<true|false>`").setEphemeral(true).queue();
            return;
        }

        String err = ensureConnectedToMemberVoice(guild, member);
        if (err != null) {
            event.reply(err).setEphemeral(true).queue();
            return;
        }

        // Must ACK within 3 seconds; if you do extra work, defer.
        event.deferReply(true).queue(); // ephemeral "thinking"

        String trackUrl = query.startsWith("http") ? query : "ytsearch:" + query;

        GuildMusicManager gm = PlayerManager.getINSTANCE().getMusicManager(guild);
        gm.scheduler.setAutoPlay(autoPlay);

        PlayerManager.getINSTANCE().loadAndPlay(textChannel, trackUrl, member);

        event.getHook().editOriginal("✅ 재생 요청을 처리했습니다.").queue();
    }

    private void handleStop(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        if (guild == null) {
            event.reply("길드 정보를 가져오지 못했습니다.").setEphemeral(true).queue();
            return;
        }

        GuildMusicManager gm = PlayerManager.getINSTANCE().getMusicManager(guild);
        gm.audioPlayer.stopTrack();
        gm.scheduler.clearQueue();
        event.reply("⏹️ 재생을 중지하고 큐를 비웠습니다.").queue();
    }

    private void handleSkip(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        if (guild == null) {
            event.reply("길드 정보를 가져오지 못했습니다.").setEphemeral(true).queue();
            return;
        }

        GuildMusicManager gm = PlayerManager.getINSTANCE().getMusicManager(guild);
        gm.scheduler.nextTrack();
        event.reply("⏭️ 다음 곡으로 건너뜁니다.").queue();
    }

    private void handleQueue(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        if (guild == null) {
            event.reply("길드 정보를 가져오지 못했습니다.").setEphemeral(true).queue();
            return;
        }

        GuildMusicManager gm = PlayerManager.getINSTANCE().getMusicManager(guild);
        List<String> list = gm.scheduler.showList();
        if (list.isEmpty()) {
            event.reply("📭 현재 대기열이 비어 있습니다.").setEphemeral(true).queue();
            return;
        }

        // Keep it short (Discord 메시지 길이 제한 고려)
        String content = String.join("\n", list.stream().limit(30).toList());
        event.reply("🎶 현재 대기열:\n" + content).setEphemeral(true).queue();
    }

    private void handleClear(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        if (guild == null) {
            event.reply("길드 정보를 가져오지 못했습니다.").setEphemeral(true).queue();
            return;
        }

        GuildMusicManager gm = PlayerManager.getINSTANCE().getMusicManager(guild);
        gm.scheduler.clearQueue();
        event.reply("🧹 대기열을 비웠습니다.").queue();
    }

    private void handlePause(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        if (guild == null) {
            event.reply("길드 정보를 가져오지 못했습니다.").setEphemeral(true).queue();
            return;
        }

        GuildMusicManager gm = PlayerManager.getINSTANCE().getMusicManager(guild);
        if (gm.audioPlayer.getPlayingTrack() == null) {
            event.reply("⏸️ 재생 중인 곡이 없습니다.").setEphemeral(true).queue();
        } else if (gm.audioPlayer.isPaused()) {
            event.reply("⚠️ 이미 일시 정지 상태입니다.").setEphemeral(true).queue();
        } else {
            gm.audioPlayer.setPaused(true);
            event.reply("⏸️ 곡을 일시 정지했습니다.").queue();
        }
    }

    private void handleResume(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        if (guild == null) {
            event.reply("길드 정보를 가져오지 못했습니다.").setEphemeral(true).queue();
            return;
        }

        GuildMusicManager gm = PlayerManager.getINSTANCE().getMusicManager(guild);
        if (gm.audioPlayer.getPlayingTrack() == null) {
            event.reply("▶️ 재생할 곡이 없습니다.").setEphemeral(true).queue();
        } else if (!gm.audioPlayer.isPaused()) {
            event.reply("⚠️ 현재 재생 중입니다.").setEphemeral(true).queue();
        } else {
            gm.audioPlayer.setPaused(false);
            event.reply("▶️ 재생을 재개했습니다.").queue();
        }
    }

    private void handleSfx(SlashCommandInteractionEvent event) {
        Member member = event.getMember();
        Guild guild = event.getGuild();
        TextChannel textChannel = event.getChannel().asTextChannel();

        if (member == null || guild == null) {
            event.reply("멤버/길드 정보를 가져오지 못했습니다.").setEphemeral(true).queue();
            return;
        }

        String file = getStringOption(event, OPT_SFX_NAME, "");
        if (file.isBlank()) {
            event.reply("효과음 이름이 비어 있습니다.").setEphemeral(true).queue();
            return;
        }

        String err = ensureConnectedToMemberVoice(guild, member);
        if (err != null) {
            event.reply(err).setEphemeral(true).queue();
            return;
        }

        String localPath = "resources/" + file; // TODO: 실제 리소스 경로에 맞게 조정 필요
        PlayerManager.getINSTANCE().loadAndPlay(textChannel, localPath, member);
        event.reply("🔊 효과음을 재생합니다: `" + file + "`").setEphemeral(true).queue();
    }

    private void handlePizza(SlashCommandInteractionEvent event) {
        event.replyEmbeds(new EmbedBuilder().setTitle("Edou").setImage(PIZZA_IMAGE).build()).queue();
    }

    private String ensureConnectedToMemberVoice(Guild guild, Member member) {
        if (member.getVoiceState() == null || !member.getVoiceState().inAudioChannel()) {
            return "⚠️ 먼저 음성 채널에 들어가세요!";
        }
        AudioChannel audio = member.getVoiceState().getChannel();
        if (audio == null) {
            return "⚠️ 음성 채널 정보를 찾지 못했습니다.";
        }

        AudioManager am = guild.getAudioManager();
        if (!guild.getSelfMember().getVoiceState().inAudioChannel()) {
            am.openAudioConnection(audio);
        }
        return null;
    }

    private String getStringOption(SlashCommandInteractionEvent event, String name, String def) {
        OptionMapping opt = event.getOption(name);
        return opt != null ? opt.getAsString() : def;
    }

    private boolean getBoolOption(SlashCommandInteractionEvent event, String name, boolean def) {
        OptionMapping opt = event.getOption(name);
        return opt != null ? opt.getAsBoolean() : def;
    }
}
