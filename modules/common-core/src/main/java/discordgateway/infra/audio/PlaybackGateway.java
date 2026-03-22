package discordgateway.infra.audio;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.interactions.commands.Command;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface PlaybackGateway {
    CompletableFuture<List<Command.Choice>> searchChoices(String query, int limit);
    void setAutoPlay(Guild guild, boolean autoPlay);
    void loadAndPlay(TextChannel textChannel, String trackUrl);
    void playLocalFile(TextChannel textChannel, String fileName);
    void stop(Guild guild);
    void skip(Guild guild);
    void clearQueue(Guild guild);
    List<String> queue(Guild guild);
    PlaybackSnapshot snapshot(Guild guild);
    void pause(Guild guild);
    void resume(Guild guild);
    CompletableFuture<Boolean> recover(Guild guild, String identifier);
}
