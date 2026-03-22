package discordgateway.playback.audio;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import discordgateway.common.command.MusicCommandTrace;
import discordgateway.common.command.MusicCommandTraceContext;
import discordgateway.common.event.MusicEvent;
import discordgateway.common.event.MusicEventFactory;
import discordgateway.common.event.MusicEventPublisher;
import discordgateway.playback.domain.GuildPlaybackLockManager;
import discordgateway.playback.domain.PlayerState;
import discordgateway.playback.domain.PlayerStateRepository;
import discordgateway.playback.domain.QueueEntry;
import discordgateway.playback.domain.QueueRepository;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;

public class TrackScheduler extends AudioEventAdapter {
    private static final int LOCK_RETRY_ATTEMPTS = 10;
    private static final long LOCK_RETRY_DELAY_NANOS = 25_000_000L;

    private final long guildId;
    private final AudioPlayer audioPlayer;
    private final AudioPlayerManager playerManager;
    private final QueueRepository queueRepository;
    private final PlayerStateRepository playerStateRepository;
    private final GuildPlaybackLockManager playbackLockManager;
    private final MusicEventPublisher musicEventPublisher;
    private final MusicEventFactory musicEventFactory;
    private final String ownerNode;
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
            GuildPlaybackLockManager playbackLockManager,
            MusicEventPublisher musicEventPublisher,
            MusicEventFactory musicEventFactory,
            String ownerNode
    ) {
        this.guildId = guildId;
        this.audioPlayer = audioPlayer;
        this.playerManager = playerManager;
        this.queueRepository = queueRepository;
        this.playerStateRepository = playerStateRepository;
        this.playbackLockManager = playbackLockManager;
        this.musicEventPublisher = musicEventPublisher;
        this.musicEventFactory = musicEventFactory;
        this.ownerNode = ownerNode;
        this.bufferedTracks = new ConcurrentHashMap<>();
        this.transitionVersion = new AtomicLong();
    }

    public void setAutoPlay(boolean autoPlay) {
        boolean changed = this.autoPlay != autoPlay;
        this.autoPlay = autoPlay;
        updatePlayerState(state -> state.setAutoPlay(autoPlay));
        if (changed) {
            musicEventPublisher.publish(musicEventFactory.autoPlayChanged(guildId, autoPlay));
        }
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
            markTrackStarted(track, MusicEvent.TransitionSource.COMMAND, null);
            return false;
        }

        enqueueTrack(track, MusicEvent.TransitionSource.COMMAND);
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

        publishTrackPlaybackChanged(
                track,
                MusicEvent.PlaybackState.FINISHED,
                MusicEvent.TransitionSource.SYSTEM,
                endReason.name()
        );
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
        boolean hadEntries = queueRepository.hasEntries(guildId);
        boolean currentTrackPreserved = audioPlayer.getPlayingTrack() != null;
        queueRepository.clear(guildId);
        bufferedTracks.clear();
        clearProcessingOnly();
        musicEventPublisher.publish(musicEventFactory.queueCleared(guildId, hadEntries, currentTrackPreserved));
    }

    public void stop() {
        transitionVersion.incrementAndGet();
        boolean hadEntries = queueRepository.hasEntries(guildId);
        AudioTrack currentTrack = audioPlayer.getPlayingTrack();
        queueRepository.clear(guildId);
        bufferedTracks.clear();
        audioPlayer.stopTrack();
        clearNowPlaying();
        musicEventPublisher.publish(musicEventFactory.queueCleared(guildId, hadEntries, false));
        publishTrackPlaybackChanged(
                currentTrack,
                MusicEvent.PlaybackState.STOPPED,
                MusicEvent.TransitionSource.COMMAND,
                "stop-command"
        );
    }

    public void pause() {
        this.audioPlayer.setPaused(true);
        updatePlayerState(state -> state.setPaused(true));
        publishTrackPlaybackChanged(
                audioPlayer.getPlayingTrack(),
                MusicEvent.PlaybackState.PAUSED,
                MusicEvent.TransitionSource.COMMAND,
                "pause-command"
        );
    }

    public void resume() {
        this.audioPlayer.setPaused(false);
        updatePlayerState(state -> state.setPaused(false));
        publishTrackPlaybackChanged(
                audioPlayer.getPlayingTrack(),
                MusicEvent.PlaybackState.RESUMED,
                MusicEvent.TransitionSource.COMMAND,
                "resume-command"
        );
    }

    public CompletableFuture<Boolean> recover(String identifier) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        MusicCommandTrace trace = MusicCommandTraceContext.current();
        if (identifier == null || identifier.isBlank()) {
            future.complete(false);
            return future;
        }

        long version = transitionVersion.incrementAndGet();
        GuildPlaybackLockManager.GuildPlaybackLock lock = acquirePlaybackLock();
        if (!lock.acquired()) {
            future.complete(false);
            return future;
        }

        audioPlayer.stopTrack();
        markProcessing(PendingLoadSource.RECOVERY);
        String loadIdentifier = toLoadIdentifier(identifier);

        playerManager.loadItemOrdered(this, loadIdentifier, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack audioTrack) {
                MusicCommandTraceContext.runWith(trace, () -> {
                    if (isTransitionCancelled(version)) {
                        lock.release();
                        future.complete(false);
                        return;
                    }
                    startResolvedTrack(lock, version, audioTrack, MusicEvent.TransitionSource.RECOVERY);
                    future.complete(true);
                });
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                MusicCommandTraceContext.runWith(trace, () -> {
                    AudioTrack first = firstTrack(playlist);
                    if (first == null || isTransitionCancelled(version)) {
                        if (!isTransitionCancelled(version)) {
                            musicEventPublisher.publish(
                                    musicEventFactory.trackLoadFailed(
                                            guildId,
                                            identifier,
                                            MusicEvent.TransitionSource.RECOVERY,
                                            "empty_playlist",
                                            "Recovery playlist did not contain any tracks."
                                    )
                            );
                            clearNowPlaying();
                        }
                        lock.release();
                        future.complete(false);
                        return;
                    }

                    startResolvedTrack(lock, version, first, MusicEvent.TransitionSource.RECOVERY);
                    future.complete(true);
                });
            }

            @Override
            public void noMatches() {
                MusicCommandTraceContext.runWith(trace, () -> {
                    if (!isTransitionCancelled(version)) {
                        musicEventPublisher.publish(
                                musicEventFactory.trackLoadFailed(
                                        guildId,
                                        identifier,
                                        MusicEvent.TransitionSource.RECOVERY,
                                        "no_matches",
                                        "Recovery target could not be resolved."
                                )
                        );
                        clearNowPlaying();
                    }
                    lock.release();
                    future.complete(false);
                });
            }

            @Override
            public void loadFailed(FriendlyException e) {
                MusicCommandTraceContext.runWith(trace, () -> {
                    if (!isTransitionCancelled(version)) {
                        musicEventPublisher.publish(
                                musicEventFactory.trackLoadFailed(
                                        guildId,
                                        identifier,
                                        MusicEvent.TransitionSource.RECOVERY,
                                        "load_failed",
                                        safeFailureMessage(e)
                                )
                        );
                        clearNowPlaying();
                    }
                    lock.release();
                    future.complete(false);
                });
            }
        });

        return future;
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
            AudioTrack currentTrack = audioPlayer.getPlayingTrack();
            audioPlayer.stopTrack();
            publishTrackPlaybackChanged(
                    currentTrack,
                    MusicEvent.PlaybackState.STOPPED,
                    MusicEvent.TransitionSource.COMMAND,
                    "skip-command"
            );
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
        MusicCommandTrace trace = MusicCommandTraceContext.current();
        AudioTrack buffered = takeBufferedTrack(entry.identifier());
        if (buffered != null) {
            startResolvedTrack(lock, version, buffered, MusicEvent.TransitionSource.QUEUE);
            return;
        }

        markProcessing(PendingLoadSource.QUEUE);
        String loadIdentifier = toLoadIdentifier(entry.identifier());
        playerManager.loadItemOrdered(this, loadIdentifier, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack audioTrack) {
                MusicCommandTraceContext.runWith(trace, () ->
                        startResolvedTrack(lock, version, audioTrack, MusicEvent.TransitionSource.QUEUE)
                );
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                MusicCommandTraceContext.runWith(trace, () -> {
                    AudioTrack first = firstTrack(playlist);
                    if (first == null) {
                        musicEventPublisher.publish(
                                musicEventFactory.trackLoadFailed(
                                        guildId,
                                        entry.identifier(),
                                        MusicEvent.TransitionSource.QUEUE,
                                        "empty_playlist",
                                        "Queued playlist did not contain any tracks."
                                )
                        );
                        continueWithQueueEntry(lock, version, queueRepository.poll(guildId), allowAutoplay);
                        return;
                    }
                    startResolvedTrack(lock, version, first, MusicEvent.TransitionSource.QUEUE);
                });
            }

            @Override
            public void noMatches() {
                MusicCommandTraceContext.runWith(trace, () -> {
                    musicEventPublisher.publish(
                            musicEventFactory.trackLoadFailed(
                                    guildId,
                                    entry.identifier(),
                                    MusicEvent.TransitionSource.QUEUE,
                                    "no_matches",
                                    "Queued identifier could not be resolved."
                            )
                    );
                    continueWithQueueEntry(lock, version, queueRepository.poll(guildId), allowAutoplay);
                });
            }

            @Override
            public void loadFailed(FriendlyException e) {
                MusicCommandTraceContext.runWith(trace, () -> {
                    musicEventPublisher.publish(
                            musicEventFactory.trackLoadFailed(
                                    guildId,
                                    entry.identifier(),
                                    MusicEvent.TransitionSource.QUEUE,
                                    "load_failed",
                                    safeFailureMessage(e)
                            )
                    );
                    continueWithQueueEntry(lock, version, queueRepository.poll(guildId), allowAutoplay);
                });
            }
        });
    }

    private void startAutoplay(GuildPlaybackLockManager.GuildPlaybackLock lock, long version) {
        MusicCommandTrace trace = MusicCommandTraceContext.current();
        markProcessing(PendingLoadSource.AUTOPLAY);
        String query = "ytsearch:" + lastTrack.getInfo().title + " " + lastTrack.getInfo().author;

        playerManager.loadItemOrdered(this, query, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack audioTrack) {
                MusicCommandTraceContext.runWith(trace, () -> {
                    startResolvedTrack(lock, version, audioTrack, MusicEvent.TransitionSource.AUTOPLAY);
                });
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                MusicCommandTraceContext.runWith(trace, () -> {
                    AudioTrack first = firstTrack(playlist);
                    if (first == null) {
                        if (!isTransitionCancelled(version)) {
                            musicEventPublisher.publish(
                                    musicEventFactory.trackLoadFailed(
                                            guildId,
                                            query,
                                            MusicEvent.TransitionSource.AUTOPLAY,
                                            "empty_playlist",
                                            "Autoplay playlist did not contain any tracks."
                                    )
                            );
                            clearNowPlaying();
                        }
                        lock.release();
                        return;
                    }

                    startResolvedTrack(lock, version, first, MusicEvent.TransitionSource.AUTOPLAY);
                });
            }

            @Override
            public void noMatches() {
                MusicCommandTraceContext.runWith(trace, () -> {
                    if (!isTransitionCancelled(version)) {
                        musicEventPublisher.publish(
                                musicEventFactory.trackLoadFailed(
                                        guildId,
                                        query,
                                        MusicEvent.TransitionSource.AUTOPLAY,
                                        "no_matches",
                                        "Autoplay search did not return a track."
                                )
                        );
                        clearNowPlaying();
                    }
                    lock.release();
                });
            }

            @Override
            public void loadFailed(FriendlyException e) {
                MusicCommandTraceContext.runWith(trace, () -> {
                    if (!isTransitionCancelled(version)) {
                        musicEventPublisher.publish(
                                musicEventFactory.trackLoadFailed(
                                        guildId,
                                        query,
                                        MusicEvent.TransitionSource.AUTOPLAY,
                                        "load_failed",
                                        safeFailureMessage(e)
                                )
                        );
                        clearNowPlaying();
                    }
                    lock.release();
                });
            }
        });
    }

    private void startResolvedTrack(
            GuildPlaybackLockManager.GuildPlaybackLock lock,
            long version,
            AudioTrack track,
            MusicEvent.TransitionSource source
    ) {
        if (isTransitionCancelled(version)) {
            lock.release();
            return;
        }

        this.lastTrack = track;
        this.audioPlayer.startTrack(track, false);
        markTrackStarted(track, source, null);
        lock.release();
    }

    private void enqueueTrack(AudioTrack track, MusicEvent.TransitionSource source) {
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
        musicEventPublisher.publish(
                musicEventFactory.trackQueued(
                        guildId,
                        toQueueIdentifier(track),
                        track.getInfo().title,
                        track.getInfo().author,
                        source
                )
        );
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

    private void markTrackStarted(
            AudioTrack track,
            MusicEvent.TransitionSource source,
            String detail
    ) {
        pendingLoadSource = PendingLoadSource.NONE;
        updatePlayerState(state -> {
            state.setNowPlaying(toQueueIdentifier(track));
            state.setPaused(false);
            state.setOwnerNode(ownerNode);
            state.setProcessingFlag(false);
        });
        publishTrackPlaybackChanged(track, MusicEvent.PlaybackState.STARTED, source, detail);
    }

    private void clearNowPlaying() {
        pendingLoadSource = PendingLoadSource.NONE;
        updatePlayerState(state -> {
            state.setNowPlaying(null);
            state.setPaused(false);
            state.setOwnerNode(ownerNode);
            state.setProcessingFlag(false);
        });
    }

    private void clearProcessingOnly() {
        pendingLoadSource = PendingLoadSource.NONE;
        updatePlayerState(state -> {
            state.setOwnerNode(ownerNode);
            state.setProcessingFlag(false);
        });
    }

    private void markProcessing(PendingLoadSource source) {
        pendingLoadSource = source;
        updatePlayerState(state -> {
            state.setOwnerNode(ownerNode);
            state.setProcessingFlag(true);
        });
    }

    private void updatePlayerState(Consumer<PlayerState> updater) {
        PlayerState state = playerStateRepository.getOrCreate(guildId);
        state.setAutoPlay(autoPlay);
        if (state.getOwnerNode() == null || state.getOwnerNode().isBlank()) {
            state.setOwnerNode(ownerNode);
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

    private void publishTrackPlaybackChanged(
            AudioTrack track,
            MusicEvent.PlaybackState state,
            MusicEvent.TransitionSource source,
            String detail
    ) {
        String identifier = track != null ? toQueueIdentifier(track) : null;
        String title = track != null ? track.getInfo().title : null;
        String author = track != null ? track.getInfo().author : null;

        musicEventPublisher.publish(
                musicEventFactory.trackPlaybackChanged(
                        guildId,
                        state,
                        identifier,
                        title,
                        author,
                        source,
                        detail
                )
        );
    }

    private String safeFailureMessage(FriendlyException e) {
        if (e == null || e.getMessage() == null || e.getMessage().isBlank()) {
            return "Unknown load failure";
        }
        return e.getMessage();
    }

    private enum PendingLoadSource {
        NONE,
        QUEUE,
        AUTOPLAY,
        RECOVERY
    }
}

