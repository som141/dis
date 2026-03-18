package discordgateway.discord;

import discordgateway.bootstrap.DiscordProperties;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

public class DiscordCommandRegistrationListener extends ListenerAdapter {

    private final DiscordProperties discordProperties;

    public DiscordCommandRegistrationListener(DiscordProperties discordProperties) {
        this.discordProperties = discordProperties;
    }

    @Override
    public void onReady(@NotNull ReadyEvent event) {
        String devGuildId = discordProperties.getDevGuildId();
        if (devGuildId != null && !devGuildId.isBlank()) {
            Guild guild = event.getJDA().getGuildById(devGuildId);
            if (guild != null) {
                guild.updateCommands().addCommands(DiscordCommandCatalog.commands()).queue();
                return;
            }
        }

        event.getJDA().updateCommands().addCommands(DiscordCommandCatalog.commands()).queue();
    }
}
