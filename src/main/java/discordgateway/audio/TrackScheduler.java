package discordgateway.audio;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import discordgateway.domain.QueueEntry;
import discordgateway.domain.QueueRepository;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class TrackScheduler extends AudioEventAdapter {
    private final long guildId;
    private final AudioPlayer audioPlayer;
    private final AudioPlayerManager playerManager;
    private final QueueRepository queueRepository;
    private final BlockingQueue<AudioTrack> queue;

    private boolean autoPlay = false;
    private AudioTrack lastTrack;
    private TextChannel lastChannel;

    public TrackScheduler(
            long guildId,
            AudioPlayer audioPlayer,
            AudioPlayerManager playerManager,
            QueueRepository queueRepository
    ) {
        this.guildId = guildId;
        this.audioPlayer = audioPlayer;
        this.playerManager = playerManager;
        this.queueRepository = queueRepository;
        this.queue = new LinkedBlockingQueue<>(100);
    }

    public void setAutoPlay(boolean autoPlay) {
        this.autoPlay = autoPlay;
    }

    public boolean isAutoPlay() {
        return autoPlay;
    }

    /**
     * @return true if added to waiting queue, false if started immediately
     */
    public boolean queue(AudioTrack track, TextChannel channel) {
        if (!this.audioPlayer.startTrack(track, true)) {
            boolean offered = this.queue.offer(track);
            if (!offered) {
                return false;
            }

            queueRepository.push(
                    guildId,
                    new QueueEntry(
                            track.getIdentifier(),
                            track.getInfo().title,
                            track.getInfo().author,
                            System.currentTimeMillis()
                    )
            );
            return true;
        }

        this.lastTrack = track;
        if (channel != null) {
            this.lastChannel = channel;
        }
        return false;
    }

    public boolean queue(AudioTrack track) {
        return queue(track, null);
    }

    @Override
    public void onTrackEnd(AudioPlayer player, AudioTrack track, AudioTrackEndReason endReason) {
        if (!endReason.mayStartNext) {
            return;
        }

        AudioTrack next = this.queue.poll();
        if (next != null) {
            queueRepository.poll(guildId);
            this.lastTrack = next;
            this.audioPlayer.startTrack(next, false);
            return;
        }

        if (autoPlay && lastTrack != null) {
            String query = "ytsearch:" + lastTrack.getInfo().title + " " + lastTrack.getInfo().author;

            playerManager.loadItem(query, new AudioLoadResultHandler() {
                @Override
                public void trackLoaded(AudioTrack audioTrack) {
                    lastTrack = audioTrack;
                    audioPlayer.startTrack(audioTrack, false);
                    if (lastChannel != null) {
                        lastChannel.sendMessage("▶️ 자동 추천 재생: " + audioTrack.getInfo().title).queue();
                    }
                }

                @Override
                public void playlistLoaded(AudioPlaylist playlist) {
                    if (playlist.getTracks().isEmpty()) {
                        if (lastChannel != null) {
                            lastChannel.sendMessage("❌ 자동재생할 추천 곡이 비어 있습니다.").queue();
                        }
                        return;
                    }

                    AudioTrack first = playlist.getSelectedTrack() != null
                            ? playlist.getSelectedTrack()
                            : playlist.getTracks().get(0);

                    lastTrack = first;
                    audioPlayer.startTrack(first, false);

                    if (lastChannel != null) {
                        lastChannel.sendMessage("▶️ 자동 추천 재생(플레이리스트): " + first.getInfo().title).queue();
                    }
                }

                @Override
                public void noMatches() {
                    if (lastChannel != null) {
                        lastChannel.sendMessage("❌ 자동재생할 추천 곡을 찾지 못했습니다.").queue();
                    }
                }

                @Override
                public void loadFailed(FriendlyException e) {
                    if (lastChannel != null) {
                        lastChannel.sendMessage("❌ 자동재생 로드 실패: " + e.getMessage()).queue();
                    }
                }
            });
        }
    }

    public List<String> showList() {
        List<String> result = new LinkedList<>();
        for (QueueEntry entry : queueRepository.list(guildId, 30)) {
            result.add(entry.displayLine());
        }
        return result;
    }

    public void nextTrack() {
        AudioTrack next = this.queue.poll();
        if (next != null) {
            queueRepository.poll(guildId);
            this.lastTrack = next;
        }
        this.audioPlayer.startTrack(next, false);
    }

    public void clearQueue() {
        this.queue.clear();
        this.queueRepository.clear(guildId);
    }
}