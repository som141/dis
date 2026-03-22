package discordgateway.infra.audio;

import discordgateway.playback.audio.PlayerManager;
import discordgateway.playback.domain.QueueRepository;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.interactions.commands.Command;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class LavaPlayerPlaybackGateway implements PlaybackGateway {

    private final PlayerManager playerManager;
    private final QueueRepository queueRepository;

    public LavaPlayerPlaybackGateway(PlayerManager playerManager, QueueRepository queueRepository) {
        this.playerManager = playerManager;
        this.queueRepository = queueRepository;
    }

    @Override
    public CompletableFuture<List<Command.Choice>> searchChoices(String query, int limit) {
        return playerManager.searchYouTubeChoices(query, limit);
    }

    @Override
    public void setAutoPlay(Guild guild, boolean autoPlay) {
        playerManager.getMusicManager(guild).getScheduler().setAutoPlay(autoPlay);
    }

    @Override
    public void loadAndPlay(TextChannel textChannel, String trackUrl) {
        playerManager.loadAndPlay(textChannel, trackUrl);
    }

    @Override
    public void playLocalFile(TextChannel textChannel, String fileName) {
        playerManager.loadAndPlay(textChannel, "resources/" + fileName);
    }

    @Override
    public void stop(Guild guild) {
        playerManager.getMusicManager(guild).getScheduler().stop();
    }

    @Override
    public void skip(Guild guild) {
        playerManager.getMusicManager(guild).getScheduler().nextTrack();
    }

    @Override
    public void clearQueue(Guild guild) {
        playerManager.getMusicManager(guild).getScheduler().clearQueue();
    }

    @Override
    public List<String> queue(Guild guild) {
        return queueRepository.list(guild.getIdLong(), 30)
                .stream()
                .map(entry -> "??" + entry.displayLine())
                .toList();
    }

    @Override
    public PlaybackSnapshot snapshot(Guild guild) {
        var musicManager = playerManager.getMusicManager(guild);
        var currentTrack = musicManager.audioPlayer.getPlayingTrack();

        return new PlaybackSnapshot(
                currentTrack != null,
                musicManager.audioPlayer.isPaused(),
                currentTrack != null ? currentTrack.getInfo().title : null
        );
    }

    @Override
    public void pause(Guild guild) {
        playerManager.getMusicManager(guild).getScheduler().pause();
    }

    @Override
    public void resume(Guild guild) {
        playerManager.getMusicManager(guild).getScheduler().resume();
    }

    @Override
    public CompletableFuture<Boolean> recover(Guild guild, String identifier) {
        return playerManager.getMusicManager(guild).getScheduler().recover(identifier);
    }
}
