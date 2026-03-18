package discordgateway.infrastructure.audio;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;

import java.util.concurrent.CompletableFuture;

public interface VoiceGateway {
    CompletableFuture<AudioChannel> findUserAudioChannel(Guild guild, long userId);
    boolean connect(Guild guild, AudioChannel target);
    void disconnect(Guild guild);
    AudioChannel connectedChannel(Guild guild);
}