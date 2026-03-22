package discordgateway.infra.discord;

import discordgateway.common.command.DiscordReferenceResolver;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

public class JdaDiscordReferenceResolver implements DiscordReferenceResolver {

    private final JdaRuntimeContext runtimeContext;

    public JdaDiscordReferenceResolver(JdaRuntimeContext runtimeContext) {
        this.runtimeContext = runtimeContext;
    }

    @Override
    public Guild resolveGuild(long guildId) {
        JDA jda = runtimeContext.current();
        if (jda == null) {
            return null;
        }
        return jda.getGuildById(guildId);
    }

    @Override
    public TextChannel resolveTextChannel(long guildId, long channelId) {
        JDA jda = runtimeContext.current();
        if (jda == null) {
            return null;
        }

        TextChannel textChannel = jda.getTextChannelById(channelId);
        if (textChannel != null) {
            return textChannel;
        }

        Guild guild = jda.getGuildById(guildId);
        if (guild == null) {
            return null;
        }

        return guild.getTextChannelById(channelId);
    }
}
