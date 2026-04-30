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
        OptionData playQuery = new OptionData(OptionType.STRING, OPT_QUERY, "Search term or URL", true)
                .setAutoComplete(true);

        OptionData playAuto = new OptionData(OptionType.BOOLEAN, OPT_AUTOPLAY, "Auto-play recommended tracks", false);

        OptionData sfxName = new OptionData(OptionType.STRING, OPT_SFX_NAME, "Sound effect name", true)
                .addChoice("gsuck", "gsuck.mp3")
                .addChoice("smbj", "smbj.mp3");

        SubcommandData stockQuote = new SubcommandData(SUB_QUOTE, "Show one or more cached stock quotes")
                .addOption(OptionType.STRING, OPT_SYMBOL, "Ticker or comma/space separated tickers", true);

        SubcommandData stockList = new SubcommandData(SUB_LIST, "Show the US Top10 stock watchlist");

        SubcommandData stockBuy = new SubcommandData(SUB_BUY, "Buy a stock by cash amount")
                .addOption(OptionType.STRING, OPT_SYMBOL, "Ticker symbol", true)
                .addOption(OptionType.STRING, OPT_AMOUNT, "Cash amount to spend", true);

        SubcommandData stockSell = new SubcommandData(SUB_SELL, "Sell a stock by quantity")
                .addOption(OptionType.STRING, OPT_SYMBOL, "Ticker symbol", true)
                .addOption(OptionType.STRING, OPT_QUANTITY, "Quantity to sell", true);

        SubcommandData stockBalance = new SubcommandData(SUB_BALANCE, "Show current cash balance");

        SubcommandData stockPortfolio = new SubcommandData(SUB_PORTFOLIO, "Show current portfolio");

        SubcommandData stockHistory = new SubcommandData(SUB_HISTORY, "Show recent trade history")
                .addOption(OptionType.INTEGER, OPT_LIMIT, "Maximum number of entries", false);

        SubcommandData stockRank = new SubcommandData(SUB_RANK, "Show guild stock ranking")
                .addOptions(new OptionData(OptionType.STRING, OPT_PERIOD, "Ranking period", true)
                        .addChoice("day", "day")
                        .addChoice("week", "week")
                        .addChoice("all", "all"));

        return List.of(
                Commands.slash(CMD_JOIN, "Join the current voice channel"),
                Commands.slash(CMD_LEAVE, "Leave the current voice channel"),
                Commands.slash(CMD_PLAY, "Play a track").addOptions(playQuery, playAuto),
                Commands.slash(CMD_STOP, "Stop playback and clear the queue"),
                Commands.slash(CMD_SKIP, "Skip the current track"),
                Commands.slash(CMD_QUEUE, "Show the current queue"),
                Commands.slash(CMD_CLEAR, "Clear the queued tracks"),
                Commands.slash(CMD_PAUSE, "Pause playback"),
                Commands.slash(CMD_RESUME, "Resume playback"),
                Commands.slash(CMD_SFX, "Play a sound effect").addOptions(sfxName),
                Commands.slash(CMD_PIZZA, "Show the pizza image"),
                Commands.slash(CMD_STOCK, "Mock stock game commands")
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
