package discordgateway.gateway.presentation.discord;

import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DiscordCommandCatalogTest {

    @Test
    void manualIncludesEveryRegisteredCommandAndFitsInOneDiscordMessage() {
        String manual = DiscordCommandCatalog.manual();

        assertThat(DiscordCommandCatalog.commands())
                .extracting(command -> command.getName())
                .contains(DiscordCommandCatalog.CMD_MAN);
        for (var command : DiscordCommandCatalog.commands()) {
            SlashCommandData slash = (SlashCommandData) command;
            if (slash.getSubcommands().isEmpty()) {
                assertThat(manual).contains("`/" + slash.getName());
            } else {
                for (SubcommandData subcommand : slash.getSubcommands()) {
                    assertThat(manual).contains("`/" + slash.getName() + " " + subcommand.getName());
                }
            }
        }
        assertThat(manual).contains(
                "`/play <query> [autoplay]`",
                "`/stock buy <symbol> <quantity> [leverage]`",
                "`/stock history [limit]`",
                "name: gsuck, smbj",
                "period: 일간, 주간, 시즌 누적"
        );
        assertThat(manual.length()).isLessThanOrEqualTo(2000);
    }

    @Test
    void includesStockCommandWithExpectedSubcommands() {
        SlashCommandData stockCommand = (SlashCommandData) DiscordCommandCatalog.commands().stream()
                .filter(command -> DiscordCommandCatalog.CMD_STOCK.equals(command.getName()))
                .findFirst()
                .orElseThrow();

        assertThat(stockCommand.getSubcommands())
                .extracting(SubcommandData::getName)
                .containsExactlyInAnyOrder(
                        DiscordCommandCatalog.SUB_QUOTE,
                        DiscordCommandCatalog.SUB_LIST,
                        DiscordCommandCatalog.SUB_BUY,
                        DiscordCommandCatalog.SUB_SELL,
                        DiscordCommandCatalog.SUB_BALANCE,
                        DiscordCommandCatalog.SUB_PORTFOLIO,
                        DiscordCommandCatalog.SUB_HISTORY,
                        DiscordCommandCatalog.SUB_RANK
                );
    }
}
