package discordgateway.gateway.application;

import discordgateway.common.command.CommandDispatchAck;
import discordgateway.common.command.MusicCommand;
import discordgateway.common.command.MusicCommandBus;
import discordgateway.common.command.MusicCommandEnvelope;
import discordgateway.common.command.MusicCommandMessageFactory;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

import java.util.concurrent.CompletableFuture;

public class MusicApplicationService {

    private final MusicCommandBus musicCommandBus;
    private final MusicCommandMessageFactory musicCommandMessageFactory;

    public MusicApplicationService(
            MusicCommandBus musicCommandBus,
            MusicCommandMessageFactory musicCommandMessageFactory
    ) {
        this.musicCommandBus = musicCommandBus;
        this.musicCommandMessageFactory = musicCommandMessageFactory;
    }

    public MusicCommandEnvelope prepareJoin(Guild guild, TextChannel textChannel, long userId) {
        return musicCommandMessageFactory.createEphemeralEnvelope(new MusicCommand.Join(
                guild.getIdLong(),
                textChannel.getIdLong(),
                userId
        ));
    }

    public MusicCommandEnvelope prepareLeave(Guild guild) {
        return musicCommandMessageFactory.createEphemeralEnvelope(new MusicCommand.Leave(guild.getIdLong()));
    }

    public MusicCommandEnvelope preparePlay(
            Guild guild,
            TextChannel textChannel,
            long userId,
            String query,
            boolean autoPlay
    ) {
        return musicCommandMessageFactory.createEphemeralEnvelope(
                new MusicCommand.Play(
                        guild.getIdLong(),
                        textChannel.getIdLong(),
                        userId,
                        query,
                        autoPlay
                )
        );
    }

    public MusicCommandEnvelope prepareStop(Guild guild) {
        return musicCommandMessageFactory.createEphemeralEnvelope(new MusicCommand.Stop(guild.getIdLong()));
    }

    public MusicCommandEnvelope prepareSkip(Guild guild) {
        return musicCommandMessageFactory.createEphemeralEnvelope(new MusicCommand.Skip(guild.getIdLong()));
    }

    public MusicCommandEnvelope prepareQueue(Guild guild) {
        return musicCommandMessageFactory.createEphemeralEnvelope(new MusicCommand.Queue(guild.getIdLong()));
    }

    public MusicCommandEnvelope prepareClear(Guild guild) {
        return musicCommandMessageFactory.createEphemeralEnvelope(new MusicCommand.Clear(guild.getIdLong()));
    }

    public MusicCommandEnvelope preparePause(Guild guild) {
        return musicCommandMessageFactory.createEphemeralEnvelope(new MusicCommand.Pause(guild.getIdLong()));
    }

    public MusicCommandEnvelope prepareResume(Guild guild) {
        return musicCommandMessageFactory.createEphemeralEnvelope(new MusicCommand.Resume(guild.getIdLong()));
    }

    public MusicCommandEnvelope preparePlaySfx(
            Guild guild,
            TextChannel textChannel,
            long userId,
            String fileName
    ) {
        return musicCommandMessageFactory.createEphemeralEnvelope(
                new MusicCommand.PlaySfx(
                        guild.getIdLong(),
                        textChannel.getIdLong(),
                        userId,
                        fileName
                )
        );
    }

    public CompletableFuture<CommandDispatchAck> dispatch(MusicCommandEnvelope envelope) {
        return musicCommandBus.dispatch(envelope);
    }
}
