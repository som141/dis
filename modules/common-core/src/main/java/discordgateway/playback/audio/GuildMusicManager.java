package discordgateway.playback.audio;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import discordgateway.common.event.MusicEventFactory;
import discordgateway.common.command.MusicCommandResultEvent;
import discordgateway.common.event.MusicEventPublisher;
import discordgateway.playback.domain.GuildPlaybackLockManager;
import discordgateway.playback.domain.PlayerStateRepository;
import discordgateway.playback.domain.QueueRepository;
import java.util.function.Consumer;

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
            String nodeName,
            Consumer<MusicCommandResultEvent> playbackResultPublisher
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
                nodeName,
                playbackResultPublisher
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
