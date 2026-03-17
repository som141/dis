package discordgateway.application;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

public interface DiscordReferenceResolver {
    Guild resolveGuild(long guildId);
    TextChannel resolveTextChannel(long guildId, long channelId);
}
