package discordgateway.audio;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import discordgateway.domain.GuildPlaybackLockManager;
import discordgateway.domain.PlayerState;
import discordgateway.domain.PlayerStateRepository;
import discordgateway.domain.QueueEntry;
import discordgateway.domain.QueueRepository;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;

public class TrackScheduler extends AudioEventAdapter {

    private static final String DEFAULT_OWNER_NODE = resolveOwnerNode();
    private static final int LOCK_RETRY_ATTEMPTS = 10;
    private static final long LOCK_RETRY_DELAY_NANOS = 25_000_000L;

    private final long guildId;
    private final AudioPlayer audioPlayer;
    private final AudioPlayerManager playerManager;
    private final QueueRepository queueRepository;
    private final PlayerStateRepository playerStateRepository;
    private final GuildPlaybackLockManager playbackLockManager;
    private final ConcurrentHashMap<String, ConcurrentLinkedDeque<AudioTrack>> bufferedTracks;
    private final AtomicLong transitionVersion;

    private boolean autoPlay = false;
    private AudioTrack lastTrack;
    private TextChannel lastChannel;
    private PendingLoadSource pendingLoadSource = PendingLoadSource.NONE;

    public TrackScheduler(
            long guildId,
            AudioPlayer audioPlayer,
            AudioPlayerManager playerManager,
            QueueRepository queueRepository,
            PlayerStateRepository playerStateRepository,
            GuildPlaybackLockManager playbackLockManager
    ) {
        this.guildId = guildId;
        this.audioPlayer = audioPlayer;
        this.playerManager = playerManager;
        this.queueRepository = queueRepository;
        this.playerStateRepository = playerStateRepository;
        this.playbackLockManager = playbackLockManager;
        this.bufferedTracks = new ConcurrentHashMap<>();
        this.transitionVersion = new AtomicLong();
    }

    public void setAutoPlay(boolean autoPlay) {
        this.autoPlay = autoPlay;
        updatePlayerState(state -> state.setAutoPlay(autoPlay));
    }

    public boolean isAutoPlay() {
        return autoPlay;
    }

    /**
     * @return true if added to waiting queue, false if started immediately
     */
    public boolean queue(AudioTrack track, TextChannel channel) {
        if (channel != null) {
            this.lastChannel = channel;
        }

        cancelPendingAutoplayIfIdle();

        if (shouldAttemptImmediateStart() && this.audioPlayer.startTrack(track, true)) {
            this.lastTrack = track;
            markTrackStarted(track);
            return false;
        }

        enqueueTrack(track);
        return true;
    }

    public boolean queue(AudioTrack track) {
        return queue(track, null);
    }

    @Override
    public void onTrackEnd(AudioPlayer player, AudioTrack track, AudioTrackEndReason endReason) {
        if (!endReason.mayStartNext) {
            return;
        }

        advancePlayback(false, true);
    }

    public List<String> showList() {
        List<String> result = new LinkedList<>();
        for (QueueEntry entry : queueRepository.list(guildId, 30)) {
            result.add(entry.displayLine());
        }
        return result;
    }

    public void nextTrack() {
        advancePlayback(true, true);
    }

    public void clearQueue() {
        transitionVersion.incrementAndGet();
        queueRepository.clear(guildId);
        bufferedTracks.clear();
        clearProcessingOnly();
    }

    public void stop() {
        transitionVersion.incrementAndGet();
        queueRepository.clear(guildId);
        bufferedTracks.clear();
        audioPlayer.stopTrack();
        clearNowPlaying();
    }

    public void pause() {
        this.audioPlayer.setPaused(true);
        updatePlayerState(state -> state.setPaused(true));
    }

    public void resume() {
        this.audioPlayer.setPaused(false);
        updatePlayerState(state -> state.setPaused(false));
    }

    private void advancePlayback(boolean interruptCurrentTrack, boolean allowAutoplay) {
        long version = interruptCurrentTrack
                ? transitionVersion.incrementAndGet()
                : transitionVersion.get();

        GuildPlaybackLockManager.GuildPlaybackLock lock = acquirePlaybackLock();
        if (!lock.acquired()) {
            return;
        }

        QueueEntry nextEntry = queueRepository.poll(guildId);
        if (interruptCurrentTrack) {
            audioPlayer.stopTrack();
        }

        continueWithQueueEntry(lock, version, nextEntry, allowAutoplay);
    }

    private void continueWithQueueEntry(
            GuildPlaybackLockManager.GuildPlaybackLock lock,
            long version,
            QueueEntry entry,
            boolean allowAutoplay
    ) {
        if (isTransitionCancelled(version)) {
            lock.release();
            return;
        }

        if (entry != null) {
            startQueuedEntry(lock, version, entry, allowAutoplay);
            return;
        }

        if (allowAutoplay && autoPlay && lastTrack != null) {
            startAutoplay(lock, version);
            return;
        }

        clearNowPlaying();
        lock.release();
    }

    private void startQueuedEntry(
            GuildPlaybackLockManager.GuildPlaybackLock lock,
            long version,
            QueueEntry entry,
            boolean allowAutoplay
    ) {
        AudioTrack buffered = takeBufferedTrack(entry.identifier());
        if (buffered != null) {
            startResolvedTrack(lock, version, buffered);
            return;
        }

        markProcessing(PendingLoadSource.QUEUE);
        String loadIdentifier = toLoadIdentifier(entry.identifier());
        playerManager.loadItemOrdered(this, loadIdentifier, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack audioTrack) {
                startResolvedTrack(lock, version, audioTrack);
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                AudioTrack first = firstTrack(playlist);
                if (first == null) {
                    continueWithQueueEntry(lock, version, queueRepository.poll(guildId), allowAutoplay);
                    return;
                }
                startResolvedTrack(lock, version, first);
            }

            @Override
            public void noMatches() {
                continueWithQueueEntry(lock, version, queueRepository.poll(guildId), allowAutoplay);
            }

            @Override
            public void loadFailed(FriendlyException e) {
                continueWithQueueEntry(lock, version, queueRepository.poll(guildId), allowAutoplay);
            }
        });
    }

    private void startAutoplay(GuildPlaybackLockManager.GuildPlaybackLock lock, long version) {
        markProcessing(PendingLoadSource.AUTOPLAY);
        String query = "ytsearch:" + lastTrack.getInfo().title + " " + lastTrack.getInfo().author;

        playerManager.loadItemOrdered(this, query, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack audioTrack) {
                startResolvedTrack(lock, version, audioTrack);
                if (lastChannel != null && !isTransitionCancelled(version)) {
                    lastChannel.sendMessage("🔁 자동 추천 재생: " + audioTrack.getInfo().title).queue();
                }
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                AudioTrack first = firstTrack(playlist);
                if (first == null) {
                    if (!isTransitionCancelled(version)) {
                        clearNowPlaying();
                    }
                    lock.release();
                    return;
                }

                startResolvedTrack(lock, version, first);
                if (lastChannel != null && !isTransitionCancelled(version)) {
                    lastChannel.sendMessage("🔁 자동 추천 재생(플레이리스트): " + first.getInfo().title).queue();
                }
            }

            @Override
            public void noMatches() {
                if (!isTransitionCancelled(version)) {
                    clearNowPlaying();
                    if (lastChannel != null) {
                        lastChannel.sendMessage("⚠️ 자동 추천 곡을 찾지 못했습니다.").queue();
                    }
                }
                lock.release();
            }

            @Override
            public void loadFailed(FriendlyException e) {
                if (!isTransitionCancelled(version)) {
                    clearNowPlaying();
                    if (lastChannel != null) {
                        lastChannel.sendMessage("⚠️ 자동 추천 로드 실패: " + e.getMessage()).queue();
                    }
                }
                lock.release();
            }
        });
    }

    private void startResolvedTrack(
            GuildPlaybackLockManager.GuildPlaybackLock lock,
            long version,
            AudioTrack track
    ) {
        if (isTransitionCancelled(version)) {
            lock.release();
            return;
        }

        this.lastTrack = track;
        this.audioPlayer.startTrack(track, false);
        markTrackStarted(track);
        lock.release();
    }

    private void enqueueTrack(AudioTrack track) {
        queueRepository.push(
                guildId,
                new QueueEntry(
                        toQueueIdentifier(track),
                        track.getInfo().title,
                        track.getInfo().author,
                        System.currentTimeMillis()
                )
        );
        bufferTrack(track);
    }

    private void bufferTrack(AudioTrack track) {
        bufferedTracks.computeIfAbsent(
                toQueueIdentifier(track),
                ignored -> new ConcurrentLinkedDeque<>()
        ).addLast(track);
    }

    private AudioTrack takeBufferedTrack(String identifier) {
        ConcurrentLinkedDeque<AudioTrack> deque = bufferedTracks.get(identifier);
        if (deque == null) {
            return null;
        }

        AudioTrack track = deque.pollFirst();
        if (deque.isEmpty()) {
            bufferedTracks.remove(identifier, deque);
        }
        return track;
    }

    private boolean shouldAttemptImmediateStart() {
        return audioPlayer.getPlayingTrack() == null
                && !queueRepository.hasEntries(guildId)
                && !playerStateRepository.getOrCreate(guildId).isProcessingFlag();
    }

    private void cancelPendingAutoplayIfIdle() {
        if (audioPlayer.getPlayingTrack() != null) {
            return;
        }
        if (pendingLoadSource != PendingLoadSource.AUTOPLAY) {
            return;
        }

        transitionVersion.incrementAndGet();
        clearNowPlaying();
    }

    private GuildPlaybackLockManager.GuildPlaybackLock acquirePlaybackLock() {
        for (int attempt = 0; attempt < LOCK_RETRY_ATTEMPTS; attempt++) {
            GuildPlaybackLockManager.GuildPlaybackLock lock = playbackLockManager.tryAcquire(guildId);
            if (lock.acquired()) {
                return lock;
            }

            if (attempt + 1 < LOCK_RETRY_ATTEMPTS) {
                LockSupport.parkNanos(LOCK_RETRY_DELAY_NANOS);
            }
        }

        return playbackLockManager.tryAcquire(guildId);
    }

    private void markTrackStarted(AudioTrack track) {
        pendingLoadSource = PendingLoadSource.NONE;
        updatePlayerState(state -> {
            state.setNowPlaying(toQueueIdentifier(track));
            state.setPaused(false);
            state.setOwnerNode(DEFAULT_OWNER_NODE);
            state.setProcessingFlag(false);
        });
    }

    private void clearNowPlaying() {
        pendingLoadSource = PendingLoadSource.NONE;
        updatePlayerState(state -> {
            state.setNowPlaying(null);
            state.setPaused(false);
            state.setOwnerNode(DEFAULT_OWNER_NODE);
            state.setProcessingFlag(false);
        });
    }

    private void clearProcessingOnly() {
        pendingLoadSource = PendingLoadSource.NONE;
        updatePlayerState(state -> {
            state.setOwnerNode(DEFAULT_OWNER_NODE);
            state.setProcessingFlag(false);
        });
    }

    private void markProcessing(PendingLoadSource source) {
        pendingLoadSource = source;
        updatePlayerState(state -> {
            state.setOwnerNode(DEFAULT_OWNER_NODE);
            state.setProcessingFlag(true);
        });
    }

    private void updatePlayerState(Consumer<PlayerState> updater) {
        PlayerState state = playerStateRepository.getOrCreate(guildId);
        state.setAutoPlay(autoPlay);
        if (state.getOwnerNode() == null || state.getOwnerNode().isBlank()) {
            state.setOwnerNode(DEFAULT_OWNER_NODE);
        }
        updater.accept(state);
        playerStateRepository.save(state);
    }

    private boolean isTransitionCancelled(long version) {
        return transitionVersion.get() != version;
    }

    private AudioTrack firstTrack(AudioPlaylist playlist) {
        if (playlist == null || playlist.getTracks().isEmpty()) {
            return null;
        }
        if (playlist.getSelectedTrack() != null) {
            return playlist.getSelectedTrack();
        }
        return playlist.getTracks().get(0);
    }

    private String toQueueIdentifier(AudioTrack track) {
        if (track.getInfo().uri != null && !track.getInfo().uri.isBlank()) {
            return track.getInfo().uri;
        }
        return track.getIdentifier();
    }

    private String toLoadIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return identifier;
        }
        if (identifier.startsWith("http://")
                || identifier.startsWith("https://")
                || identifier.startsWith("ytsearch:")) {
            return identifier;
        }
        if (identifier.matches("^[A-Za-z0-9_-]{11}$")) {
            return "https://www.youtube.com/watch?v=" + identifier;
        }
        return identifier;
    }

    private static String resolveOwnerNode() {
        String[] keys = {"APP_NODE_NAME", "HOSTNAME", "COMPUTERNAME"};
        for (String key : keys) {
            String value = System.getenv(key);
            if (value == null) {
                continue;
            }

            String trimmed = value.trim();
            if (!trimmed.isEmpty()) {
                return trimmed;
            }
        }
        return "discord-gateway";
    }

    private enum PendingLoadSource {
        NONE,
        QUEUE,
        AUTOPLAY
    }
}
