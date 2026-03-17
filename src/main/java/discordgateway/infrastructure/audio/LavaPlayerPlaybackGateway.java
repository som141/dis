package discordgateway.infrastructure.audio;

import discordgateway.audio.GuildMusicManager;
import discordgateway.audio.PlayerManager;
import discordgateway.domain.GuildPlaybackLockManager;
import discordgateway.domain.PlayerStateRepository;
import discordgateway.domain.QueueRepository;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.interactions.commands.Command;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class LavaPlayerPlaybackGateway implements PlaybackGateway {

    private final PlayerManager playerManager;
    private final QueueRepository queueRepository;

    public LavaPlayerPlaybackGateway(
            QueueRepository queueRepository,
            PlayerStateRepository playerStateRepository,
            GuildPlaybackLockManager playbackLockManager
    ) {
        this.playerManager = PlayerManager.getINSTANCE();
        this.queueRepository = queueRepository;
        this.playerManager.setQueueRepository(queueRepository);
        this.playerManager.setPlayerStateRepository(playerStateRepository);
        this.playerManager.setPlaybackLockManager(playbackLockManager);
    }

    @Override
    public CompletableFuture<List<Command.Choice>> searchChoices(String query, int limit) {
        return playerManager.searchYouTubeChoices(query, limit);
    }

    @Override
    public void setAutoPlay(Guild guild, boolean autoPlay) {
        GuildMusicManager musicManager = playerManager.getMusicManager(guild);
        musicManager.getScheduler().setAutoPlay(autoPlay);
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
                .map(entry -> "• " + entry.displayLine())
                .toList();
    }

    @Override
    public PlaybackSnapshot snapshot(Guild guild) {
        GuildMusicManager musicManager = playerManager.getMusicManager(guild);
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
}
