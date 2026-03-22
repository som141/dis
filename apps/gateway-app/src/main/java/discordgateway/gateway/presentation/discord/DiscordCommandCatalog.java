package discordgateway.gateway.presentation.discord;

import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.util.List;

public final class DiscordCommandCatalog {

    public static final String PIZZA_IMAGE =
            "https://i.namu.wiki/i/fnE10XPWcq13FQcHrKhGdFBC4gJbwvQIm2uBHVfjR0b5CBEoS7d72f4wCapkmtS6mwQPhDO6L1-VsAfP_nvR0vaedr4eg8mN-eOfPVbR3EGdlIAJhi6qEtgAkbLCZUfjewnMR18sLcz_u_-wsSGrsA.webp";

    public static final String CMD_JOIN = "join";
    public static final String CMD_LEAVE = "leave";
    public static final String CMD_PLAY = "play";
    public static final String CMD_STOP = "stop";
    public static final String CMD_SKIP = "skip";
    public static final String CMD_QUEUE = "queue";
    public static final String CMD_CLEAR = "clear";
    public static final String CMD_PAUSE = "pause";
    public static final String CMD_RESUME = "resume";
    public static final String CMD_SFX = "sfx";
    public static final String CMD_PIZZA = "pizza";

    public static final String OPT_QUERY = "query";
    public static final String OPT_AUTOPLAY = "autoplay";
    public static final String OPT_SFX_NAME = "name";

    private DiscordCommandCatalog() {
    }

    public static List<net.dv8tion.jda.api.interactions.commands.build.CommandData> commands() {
        OptionData playQuery = new OptionData(OptionType.STRING, OPT_QUERY, "검색어 또는 URL", true)
                .setAutoComplete(true);

        OptionData playAuto = new OptionData(OptionType.BOOLEAN, OPT_AUTOPLAY, "곡이 끝나면 자동 추천 재생", false);

        OptionData sfxName = new OptionData(OptionType.STRING, OPT_SFX_NAME, "재생할 효과음", true)
                .addChoice("gsuck", "gsuck.mp3")
                .addChoice("smbj", "smbj.mp3");

        return List.of(
                Commands.slash(CMD_JOIN, "현재 음성 채널로 봇을 입장시킵니다"),
                Commands.slash(CMD_LEAVE, "봇을 음성 채널에서 퇴장시킵니다"),
                Commands.slash(CMD_PLAY, "음악을 재생합니다").addOptions(playQuery, playAuto),
                Commands.slash(CMD_STOP, "재생을 중지하고 큐를 비웁니다"),
                Commands.slash(CMD_SKIP, "다음 곡으로 건너뜁니다"),
                Commands.slash(CMD_QUEUE, "현재 대기열을 표시합니다"),
                Commands.slash(CMD_CLEAR, "대기열을 비웁니다(재생 중인 곡은 유지)"),
                Commands.slash(CMD_PAUSE, "현재 곡을 일시정지합니다"),
                Commands.slash(CMD_RESUME, "일시정지를 해제합니다"),
                Commands.slash(CMD_SFX, "로컬 효과음을 재생합니다").addOptions(sfxName),
                Commands.slash(CMD_PIZZA, "피자 이미지를 출력합니다")
        );
    }
}
