package sp1.audio;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class TrackScheduler extends AudioEventAdapter {
    private final AudioPlayer audioPlayer;
    private final AudioPlayerManager playerManager;
    private final BlockingQueue<AudioTrack> queue;

    // ✅ 자동재생 관련 상태
    private boolean autoPlay = false;
    private AudioTrack lastTrack;
    private TextChannel lastChannel;

    public TrackScheduler(AudioPlayer audioPlayer, AudioPlayerManager playerManager) {
        this.audioPlayer = audioPlayer;
        this.playerManager = playerManager;
        this.queue = new LinkedBlockingQueue<>();
    }

    // ✅ Listeners 쪽에서 -l 옵션으로 켜고 끄는 플래그
    public void setAutoPlay(boolean autoPlay) {
        this.autoPlay = autoPlay;
    }

    public boolean isAutoPlay() {
        return autoPlay;
    }

    // ✅ 텍스트 채널까지 같이 받아서 마지막 채널 기억
    public void queue(AudioTrack track, TextChannel channel) {
        if (!this.audioPlayer.startTrack(track, true)) {
            this.queue.offer(track);
        }
        this.lastTrack = track;
        if (channel != null) {
            this.lastChannel = channel;
        }
    }

    // 옛 코드 호환용 (채널 필요 없을 때)
    public void queue(AudioTrack track) {
        queue(track, null);
    }

    @Override
    public void onTrackEnd(AudioPlayer player, AudioTrack track, AudioTrackEndReason endReason) {
        if (!endReason.mayStartNext) {
            return;
        }

        // 1) 먼저 큐에 다음 곡이 있으면 그거부터 재생
        AudioTrack next = this.queue.poll();
        if (next != null) {
            this.lastTrack = next;
            this.audioPlayer.startTrack(next, false);
            return;
        }

        // 2) 큐가 비어 있고, autoPlay가 켜져 있고, 마지막 곡 정보가 있으면 → 추천곡 1개 뽑기
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
        for (AudioTrack at : queue) {
            result.add(at.getInfo().title + " - " + at.getInfo().author);
        }
        return result;
    }

    public void nextTrack() {
        AudioTrack next = this.queue.poll();
        if (next != null) {
            this.lastTrack = next;
        }
        this.audioPlayer.startTrack(next, false);
    }

    public void clearQueue() {
        this.queue.clear();
    }
}
