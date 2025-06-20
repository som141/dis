package sp1.audio;

import com.sedmelluq.discord.lavaplayer.player.*;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import net.dv8tion.jda.api.audio.AudioSendHandler;
import com.sedmelluq.discord.lavaplayer.track.playback.AudioFrame;

public class GuildMusicManager {
    public final AudioPlayer player;
    public final TrackScheduler scheduler;

    public GuildMusicManager(AudioPlayerManager manager) {
        this.player = manager.createPlayer();
        this.scheduler = new TrackScheduler(player);
        this.player.addListener(this.scheduler);
    }

    public AudioSendHandler getSendHandler() {
        return new AudioPlayerSendHandler(player);
    }
}
