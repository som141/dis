package discordgateway.application;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

import java.util.concurrent.CompletableFuture;

public class MusicApplicationService {

    private final MusicCommandBus musicCommandBus;

    public MusicApplicationService(MusicCommandBus musicCommandBus) {
        this.musicCommandBus = musicCommandBus;
    }

    public CompletableFuture<CommandResult> join(Guild guild, long userId) {
        return musicCommandBus.dispatch(new MusicCommand.Join(guild.getIdLong(), userId));
    }

    public CommandResult leave(Guild guild) {
        return dispatchSync(new MusicCommand.Leave(guild.getIdLong()));
    }

    public CompletableFuture<CommandResult> play(
            Guild guild,
            TextChannel textChannel,
            long userId,
            String query,
            boolean autoPlay
    ) {
        return musicCommandBus.dispatch(
                new MusicCommand.Play(
                        guild.getIdLong(),
                        textChannel.getIdLong(),
                        userId,
                        query,
                        autoPlay
                )
        );
    }

    public CommandResult stop(Guild guild) {
        return dispatchSync(new MusicCommand.Stop(guild.getIdLong()));
    }

    public CommandResult skip(Guild guild) {
        return dispatchSync(new MusicCommand.Skip(guild.getIdLong()));
    }

    public CommandResult queue(Guild guild) {
        return dispatchSync(new MusicCommand.Queue(guild.getIdLong()));
    }

    public CommandResult clear(Guild guild) {
        return dispatchSync(new MusicCommand.Clear(guild.getIdLong()));
    }

    public CommandResult pause(Guild guild) {
        return dispatchSync(new MusicCommand.Pause(guild.getIdLong()));
    }

    public CommandResult resume(Guild guild) {
        return dispatchSync(new MusicCommand.Resume(guild.getIdLong()));
    }

    public CompletableFuture<CommandResult> playSfx(
            Guild guild,
            TextChannel textChannel,
            long userId,
            String fileName
    ) {
        return musicCommandBus.dispatch(
                new MusicCommand.PlaySfx(
                        guild.getIdLong(),
                        textChannel.getIdLong(),
                        userId,
                        fileName
                )
        );
    }

    private CommandResult dispatchSync(MusicCommand command) {
        return musicCommandBus.dispatch(command).join();
    }
}
