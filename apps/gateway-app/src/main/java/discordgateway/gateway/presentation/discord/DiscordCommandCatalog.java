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
    public static final String OPT_QUANTITY = "quantity";
    public static final String OPT_LEVERAGE = "leverage";
    public static final String OPT_LIMIT = "limit";
    public static final String OPT_PERIOD = "period";

    public static final String SUB_QUOTE = "quote";
    public static final String SUB_LIST = "list";
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

        OptionData playAuto = new OptionData(OptionType.BOOLEAN, OPT_AUTOPLAY, "추천 곡까지 자동 재생", false);

        OptionData sfxName = new OptionData(OptionType.STRING, OPT_SFX_NAME, "효과음 이름", true)
                .addChoice("gsuck", "gsuck.mp3")
                .addChoice("smbj", "smbj.mp3");

        SubcommandData stockQuote = new SubcommandData(SUB_QUOTE, "원하는 주식 시세를 조회합니다")
                .addOption(OptionType.STRING, OPT_SYMBOL, "종목 코드 또는 쉼표/공백으로 구분한 여러 코드", true);

        SubcommandData stockList = new SubcommandData(SUB_LIST, "미국 시가총액 상위 10개 종목을 보여줍니다");

        SubcommandData stockBuy = new SubcommandData(SUB_BUY, "수량 기준으로 주식을 매수합니다")
                .addOption(OptionType.STRING, OPT_SYMBOL, "주식 종목 코드", true)
                .addOption(OptionType.INTEGER, OPT_QUANTITY, "매수할 주 수", true)
                .addOption(OptionType.INTEGER, OPT_LEVERAGE, "레버리지 배수 1~50", false);

        SubcommandData stockSell = new SubcommandData(SUB_SELL, "수량 기준으로 주식을 매도합니다")
                .addOption(OptionType.STRING, OPT_SYMBOL, "주식 종목 코드", true)
                .addOption(OptionType.INTEGER, OPT_QUANTITY, "매도할 주 수", true);

        SubcommandData stockBalance = new SubcommandData(SUB_BALANCE, "이번 시즌 보유 현금을 확인합니다");

        SubcommandData stockPortfolio = new SubcommandData(SUB_PORTFOLIO, "현재 보유 종목을 확인합니다");

        SubcommandData stockHistory = new SubcommandData(SUB_HISTORY, "최근 거래 내역을 확인합니다")
                .addOption(OptionType.INTEGER, OPT_LIMIT, "최대 조회 개수", false);

        SubcommandData stockRank = new SubcommandData(SUB_RANK, "서버 수익률 순위를 확인합니다")
                .addOptions(new OptionData(OptionType.STRING, OPT_PERIOD, "순위를 계산할 기간", true)
                        .addChoice("일간", "day")
                        .addChoice("주간", "week")
                        .addChoice("시즌 누적", "all"));

        return List.of(
                Commands.slash(CMD_JOIN, "현재 음성 채널에 입장합니다"),
                Commands.slash(CMD_LEAVE, "현재 음성 채널에서 나갑니다"),
                Commands.slash(CMD_PLAY, "음악을 재생합니다").addOptions(playQuery, playAuto),
                Commands.slash(CMD_STOP, "재생을 멈추고 대기열을 비웁니다"),
                Commands.slash(CMD_SKIP, "현재 곡을 건너뜁니다"),
                Commands.slash(CMD_QUEUE, "현재 대기열을 확인합니다"),
                Commands.slash(CMD_CLEAR, "대기열을 비웁니다"),
                Commands.slash(CMD_PAUSE, "재생을 일시정지합니다"),
                Commands.slash(CMD_RESUME, "재생을 다시 시작합니다"),
                Commands.slash(CMD_SFX, "효과음을 재생합니다").addOptions(sfxName),
                Commands.slash(CMD_PIZZA, "피자 이미지를 보여줍니다"),
                Commands.slash(CMD_STOCK, "모의투자 게임 명령입니다")
                        .addSubcommands(
                                stockQuote,
                                stockList,
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
