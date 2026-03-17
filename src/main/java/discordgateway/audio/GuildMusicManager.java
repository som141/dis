package discordgateway.audio;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import discordgateway.application.event.MusicEventFactory;
import discordgateway.application.event.MusicEventPublisher;
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
            GuildPlaybackLockManager playbackLockManager,
            MusicEventPublisher musicEventPublisher,
            MusicEventFactory musicEventFactory,
            String nodeName
    ) {
        this.audioPlayer = manager.createPlayer();
        this.scheduler = new TrackScheduler(
                guildId,
                this.audioPlayer,
                manager,
                queueRepository,
                playerStateRepository,
                playbackLockManager,
                musicEventPublisher,
                musicEventFactory,
                nodeName
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
