package discordgateway.infra.audio;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.managers.AudioManager;

import java.util.concurrent.CompletableFuture;

public class JdaVoiceGateway implements VoiceGateway {

    @Override
    public CompletableFuture<AudioChannel> findUserAudioChannel(Guild guild, long userId) {
        CompletableFuture<AudioChannel> future = new CompletableFuture<>();

        guild.retrieveMemberVoiceStateById(userId).queue(
                voiceState -> {
                    if (voiceState == null || !voiceState.inAudioChannel()) {
                        future.completeExceptionally(new IllegalStateException("User is not in a voice channel"));
                        return;
                    }

                    AudioChannel channel = voiceState.getChannel();
                    if (channel == null) {
                        future.completeExceptionally(new IllegalStateException("Voice channel not found"));
                        return;
                    }

                    future.complete(channel);
                },
                future::completeExceptionally
        );

        return future;
    }

    @Override
    public boolean connect(Guild guild, AudioChannel target) {
        AudioManager audioManager = guild.getAudioManager();

        AudioChannel connected = audioManager.getConnectedChannel();
        if (connected != null && connected.getIdLong() == target.getIdLong()) {
            return true;
        }

        if (!guild.getSelfMember().hasPermission(target, Permission.VOICE_CONNECT)) {
            throw new IllegalStateException("봇에 VOICE_CONNECT 권한이 없습니다.");
        }

        audioManager.setSelfDeafened(true);
        audioManager.openAudioConnection(target);
        return false;
    }

    @Override
    public void disconnect(Guild guild) {
        guild.getAudioManager().closeAudioConnection();
    }

    @Override
    public AudioChannel connectedChannel(Guild guild) {
        return guild.getAudioManager().getConnectedChannel();
    }
}
