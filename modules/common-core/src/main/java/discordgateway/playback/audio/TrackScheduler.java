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
import discordgateway.common.command.MusicCommandResponseMode;
import discordgateway.common.command.MusicCommandResultEvent;
import discordgateway.common.event.MusicEvent;
import discordgateway.common.event.MusicEventFactory;
import discordgateway.common.event.MusicEventPublisher;
import discordgateway.playback.domain.GuildPlaybackLockManager;
import discordgateway.playback.domain.PlayerState;
import discordgateway.playback.domain.PlayerStateRepository;
import discordgateway.playback.domain.QueueEntry;
import discordgateway.playback.domain.QueueRepository;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.List;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;

public class TrackScheduler extends AudioEventAdapter {
    private static final Logger log = LoggerFactory.getLogger(TrackScheduler.class);
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
    private final Consumer<MusicCommandResultEvent> playbackResultPublisher;
    private final ConcurrentHashMap<AudioTrack, PlaybackRequest> playbackRequests = new ConcurrentHashMap<>();
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
            String ownerNode,
            Consumer<MusicCommandResultEvent> playbackResultPublisher
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
        this.playbackResultPublisher = playbackResultPublisher;
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
        preparePlaybackRequest(track, MusicEvent.TransitionSource.COMMAND);

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
            playbackRequests.remove(track);
            return;
        }

        PlaybackRequest request = playbackRequests.get(track);
        if (endReason == AudioTrackEndReason.LOAD_FAILED) {
            reportPlaybackFailure(track, "playback_load_failed", null);
        } else if (request == null || !request.failureReported.get()) {
            publishTrackPlaybackChanged(
                    track,
                    MusicEvent.PlaybackState.FINISHED,
                    MusicEvent.TransitionSource.SYSTEM,
                    endReason.name()
            );
        }
        playbackRequests.remove(track);
        advancePlayback(false, true);
    }

    @Override
    public void onTrackException(AudioPlayer player, AudioTrack track, FriendlyException exception) {
        reportPlaybackFailure(track, "playback_exception", exception);
        // LavaPlayer emits the end event separately; advancing here would skip the next queued track twice.
    }

    @Override
    public void onTrackStuck(AudioPlayer player, AudioTrack track, long thresholdMs) {
        if (!playbackRequests.containsKey(track)) {
            return;
        }
        reportPlaybackFailure(track, "playback_stuck", null);
        advanceStuckTrack(track);
    }

    private void advanceStuckTrack(AudioTrack expectedTrack) {
        GuildPlaybackLockManager.GuildPlaybackLock lock = acquirePlaybackLock();
        if (!lock.acquired()) {
            return;
        }
        if (audioPlayer.getPlayingTrack() != expectedTrack) {
            lock.release();
            return;
        }
        long version = transitionVersion.incrementAndGet();
        audioPlayer.stopTrack();
        continueWithQueueEntry(lock, version, queueRepository.poll(guildId), true);
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
        clearBufferedTracks();
        clearProcessingOnly();
        musicEventPublisher.publish(musicEventFactory.queueCleared(guildId, hadEntries, currentTrackPreserved));
    }

    public void stop() {
        transitionVersion.incrementAndGet();
        boolean hadEntries = queueRepository.hasEntries(guildId);
        AudioTrack currentTrack = audioPlayer.getPlayingTrack();
        queueRepository.clear(guildId);
        clearBufferedTracks();
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
        preparePlaybackRequest(track, source);
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

    private void clearBufferedTracks() {
        bufferedTracks.values().forEach(tracks -> tracks.forEach(playbackRequests::remove));
        bufferedTracks.clear();
    }

    private void preparePlaybackRequest(AudioTrack track, MusicEvent.TransitionSource source) {
        // YouTubeSource owns AudioTrack.userData (including optional OAuth data); keep routing separate.
        playbackRequests.compute(track, (ignored, existing) -> {
            if (existing != null) {
                existing.source = source;
                return existing;
            }
            MusicCommandTrace trace = source == MusicEvent.TransitionSource.COMMAND
                    ? MusicCommandTraceContext.current() : null;
            return new PlaybackRequest(trace, source);
        });
    }

    private void reportPlaybackFailure(AudioTrack track, String failureType, Throwable failure) {
        PlaybackRequest request = playbackRequests.get(track);
        if (request == null || !request.failureReported.compareAndSet(false, true)) {
            return;
        }
        String explanation = safePlaybackFailureExplanation(failure);
        MusicCommandTraceContext.runWith(request.trace, () -> {
            // Raw exceptions may contain signed media URLs or credentials; keep diagnostics classified.
            log.atWarn().addKeyValue("guildId", guildId)
                    .addKeyValue("failureType", failureType)
                    .addKeyValue("exceptionType", failure == null ? null : failure.getClass().getSimpleName())
                    .addKeyValue("reason", explanation)
                    .log("track playback failed");
            musicEventPublisher.publish(musicEventFactory.trackLoadFailed(guildId, toQueueIdentifier(track),
                    request.source, failureType, explanation));

            MusicCommandTrace trace = request.trace;
            if (trace == null || trace.responseTargetNode() == null || trace.responseTargetNode().isBlank()) {
                return;
            }
            String title = track.getInfo().title;
            title = title == null || title.isBlank() ? "제목 없는 곡" : title.replaceAll("[\\r\\n]", " ");
            if (title.length() > 160) {
                title = title.substring(0, 160);
            }
            try {
                playbackResultPublisher.accept(new MusicCommandResultEvent(trace.commandId(), trace.schemaVersion(),
                        System.currentTimeMillis(), ownerNode, trace.responseTargetNode(), guildId, false,
                        "재생에 실패했습니다: " + title + "\n" + explanation,
                        trace.responseMode() == MusicCommandResponseMode.EPHEMERAL, "PLAYBACK_FAILED"));
            } catch (RuntimeException notificationFailure) {
                log.atWarn().addKeyValue("guildId", guildId).addKeyValue("commandId", trace.commandId())
                        .addKeyValue("exceptionType", notificationFailure.getClass().getSimpleName())
                        .log("playback failure reply could not be published");
            }
        });
    }

    private String safePlaybackFailureExplanation(Throwable failure) {
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        ArrayDeque<Throwable> causes = new ArrayDeque<>();
        if (failure != null) {
            causes.add(failure);
        }
        boolean loginRequired = false;
        while (!causes.isEmpty() && visited.size() < 100) {
            Throwable cause = causes.removeFirst();
            if (!visited.add(cause)) {
                continue;
            }
            String message = cause.getMessage() == null ? "" : cause.getMessage().toLowerCase(Locale.ROOT);
            if (message.matches("(?s).*(?:status|response|http)[^\\r\\n]{0,32}\\b403\\b.*")) {
                return "음원 제공 서버가 접근을 거부했습니다 (HTTP 403). 다른 곡을 시도하거나 잠시 후 다시 시도해 주세요.";
            }
            loginRequired |= message.contains("sign in") || message.contains("login required")
                    || message.contains("requires login") || message.contains("confirm you're not a bot");
            if (cause.getCause() != null) {
                causes.add(cause.getCause());
            }
            Collections.addAll(causes, cause.getSuppressed());
        }
        return loginRequired
                ? "YouTube에서 로그인 확인을 요구하고 있습니다. 잠시 후 다른 곡으로 다시 시도해 주세요."
                : "음원 데이터를 재생하지 못했습니다. 잠시 후 다른 곡으로 다시 시도해 주세요.";
    }

    private static final class PlaybackRequest {
        private final MusicCommandTrace trace;
        private volatile MusicEvent.TransitionSource source;
        private final AtomicBoolean failureReported = new AtomicBoolean();

        private PlaybackRequest(MusicCommandTrace trace, MusicEvent.TransitionSource source) {
            this.trace = trace;
            this.source = source;
        }
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
