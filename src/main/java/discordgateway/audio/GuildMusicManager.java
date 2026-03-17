package discordgateway.audio;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import discordgateway.domain.GuildPlaybackLockManager;
import discordgateway.domain.PlayerStateRepository;
import discordgateway.domain.QueueRepository;

public class GuildMusicManager {

    public final AudioPlayer audioPlayer;
    public final TrackScheduler scheduler;
    private final AudioPlayerSendHandler sendHandler;

    public GuildMusicManager(
            long guildId,
            AudioPlayerManager manager,
            QueueRepository queueRepository,
            PlayerStateRepository playerStateRepository,
            GuildPlaybackLockManager playbackLockManager
    ) {
        this.audioPlayer = manager.createPlayer();
        this.scheduler = new TrackScheduler(
                guildId,
                this.audioPlayer,
                manager,
                queueRepository,
                playerStateRepository,
                playbackLockManager
        );
        this.audioPlayer.addListener(this.scheduler);
        this.sendHandler = new AudioPlayerSendHandler(this.audioPlayer);
    }

    public AudioPlayerSendHandler getSendHandler() {
        return this.sendHandler;
    }

    public TrackScheduler getScheduler() {
        return this.scheduler;
    }
}
