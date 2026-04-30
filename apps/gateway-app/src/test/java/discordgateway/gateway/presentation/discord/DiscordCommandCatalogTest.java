package discordgateway.gateway.presentation.discord;

import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DiscordCommandCatalogTest {

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
                        DiscordCommandCatalog.SUB_BUY,
                        DiscordCommandCatalog.SUB_SELL,
                        DiscordCommandCatalog.SUB_BALANCE,
                        DiscordCommandCatalog.SUB_PORTFOLIO,
                        DiscordCommandCatalog.SUB_HISTORY,
                        DiscordCommandCatalog.SUB_RANK
                );
    }
}
