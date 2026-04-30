package discordgateway.gateway.presentation.discord;

import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

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
    public static final String CMD_STOCK = "stock";

    public static final String OPT_QUERY = "query";
    public static final String OPT_AUTOPLAY = "autoplay";
    public static final String OPT_SFX_NAME = "name";
    public static final String OPT_SYMBOL = "symbol";
    public static final String OPT_AMOUNT = "amount";
    public static final String OPT_QUANTITY = "quantity";
    public static final String OPT_LIMIT = "limit";
    public static final String OPT_PERIOD = "period";

    public static final String SUB_QUOTE = "quote";
    public static final String SUB_BUY = "buy";
    public static final String SUB_SELL = "sell";
    public static final String SUB_BALANCE = "balance";
    public static final String SUB_PORTFOLIO = "portfolio";
    public static final String SUB_HISTORY = "history";
    public static final String SUB_RANK = "rank";

    private DiscordCommandCatalog() {
    }

    public static List<CommandData> commands() {
        OptionData playQuery = new OptionData(OptionType.STRING, OPT_QUERY, "검색어 또는 URL", true)
                .setAutoComplete(true);

        OptionData playAuto = new OptionData(OptionType.BOOLEAN, OPT_AUTOPLAY, "곡이 끝나면 자동 추천 재생", false);

        OptionData sfxName = new OptionData(OptionType.STRING, OPT_SFX_NAME, "재생할 효과음", true)
                .addChoice("gsuck", "gsuck.mp3")
                .addChoice("smbj", "smbj.mp3");

        SubcommandData stockQuote = new SubcommandData(SUB_QUOTE, "종목 시세를 조회합니다")
                .addOption(OptionType.STRING, OPT_SYMBOL, "조회할 종목 심볼", true);

        SubcommandData stockBuy = new SubcommandData(SUB_BUY, "금액 기준으로 종목을 매수합니다")
                .addOption(OptionType.STRING, OPT_SYMBOL, "매수할 종목 심볼", true)
                .addOption(OptionType.STRING, OPT_AMOUNT, "매수 금액", true);

        SubcommandData stockSell = new SubcommandData(SUB_SELL, "수량 기준으로 종목을 매도합니다")
                .addOption(OptionType.STRING, OPT_SYMBOL, "매도할 종목 심볼", true)
                .addOption(OptionType.STRING, OPT_QUANTITY, "매도 수량", true);

        SubcommandData stockBalance = new SubcommandData(SUB_BALANCE, "현재 현금 잔고를 조회합니다");

        SubcommandData stockPortfolio = new SubcommandData(SUB_PORTFOLIO, "보유 포트폴리오를 조회합니다");

        SubcommandData stockHistory = new SubcommandData(SUB_HISTORY, "최근 거래 내역을 조회합니다")
                .addOption(OptionType.INTEGER, OPT_LIMIT, "조회할 최대 건수", false);

        SubcommandData stockRank = new SubcommandData(SUB_RANK, "랭킹 기능 준비 상태를 조회합니다")
                .addOptions(new OptionData(OptionType.STRING, OPT_PERIOD, "랭킹 기간", true)
                        .addChoice("day", "day")
                        .addChoice("week", "week")
                        .addChoice("all", "all"));

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
                Commands.slash(CMD_PIZZA, "피자 이미지를 출력합니다"),
                Commands.slash(CMD_STOCK, "모의투자 명령")
                        .addSubcommands(
                                stockQuote,
                                stockBuy,
                                stockSell,
                                stockBalance,
                                stockPortfolio,
                                stockHistory,
                                stockRank
                        )
        );
    }
}
