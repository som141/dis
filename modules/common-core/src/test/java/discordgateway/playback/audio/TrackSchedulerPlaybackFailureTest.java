package discordgateway.playback.audio;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import discordgateway.common.bootstrap.AppProperties;
import discordgateway.common.bootstrap.YouTubeProperties;
import discordgateway.common.command.MusicCommand;
import discordgateway.common.command.MusicCommandEnvelope;
import discordgateway.common.command.MusicCommandMessage;
import discordgateway.common.command.MusicCommandResponseMode;
import discordgateway.common.command.MusicCommandResultEvent;
import discordgateway.common.command.MusicCommandTrace;
import discordgateway.common.command.MusicCommandTraceContext;
import discordgateway.common.event.MusicEvent;
import discordgateway.common.event.MusicEventFactory;
import discordgateway.playback.domain.GuildPlaybackLockManager;
import discordgateway.playback.domain.PlayerState;
import discordgateway.playback.domain.PlayerStateRepository;
import discordgateway.playback.domain.QueueEntry;
import discordgateway.playback.domain.QueueRepository;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.managers.AudioManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class TrackSchedulerPlaybackFailureTest {

    @TempDir Path directory;
    private AudioPlayerManager manager;
    private AudioPlayer player;
    private TrackScheduler scheduler;
    private final QueueStore queue = new QueueStore();
    private final StateStore states = new StateStore();
    private final BlockingQueue<MusicCommandResultEvent> replies = new LinkedBlockingQueue<>();
    private final ConcurrentLinkedQueue<MusicEvent> events = new ConcurrentLinkedQueue<>();
    private final GuildPlaybackLockManager locks = id -> new GuildPlaybackLockManager.GuildPlaybackLock() {
        public boolean acquired() { return true; }
        public void release() { }
    };

    @BeforeEach
    void createPlayer() {
        manager = new DefaultAudioPlayerManager();
        AudioSourceManagers.registerLocalSource(manager);
        player = manager.createPlayer();
        scheduler = new TrackScheduler(10L, player, manager, queue, states, locks, events::add,
                new MusicEventFactory(new AppProperties()), "audio-node-test", replies::add);
        player.addListener(scheduler);
    }

    @AfterEach
    void closePlayer() {
        player.destroy();
        manager.shutdown();
    }

    @Test
    void realDecoderFailureAfterMetadataLoadingRepliesToTheOriginalEnvelopeExactlyOnce() throws Exception {
        Path file = wave("deleted-after-load.wav");
        AudioTrack track = load(file);
        Files.delete(file);
        assertFalse(MusicCommandTraceContext.callWith(trace("play-1", "response-gateway"), () -> scheduler.queue(track)));

        MusicCommandResultEvent reply = replies.poll(5, TimeUnit.SECONDS);
        assertNotNull(reply);
        assertEquals("play-1", reply.commandId());
        assertEquals("response-gateway", reply.targetNode());
        assertEquals("audio-node-test", reply.producer());
        assertFalse(reply.success());
        assertTrue(reply.ephemeral());
        assertEquals("PLAYBACK_FAILED", reply.resultType());
        assertTrue(reply.message().startsWith("재생에 실패했습니다:"));
        assertFalse(reply.message().contains(file.toString()));

        consumeUntilEnded();
        assertNull(replies.poll());
        assertEquals(1, events.stream().filter(MusicEvent.TrackLoadFailed.class::isInstance).count());
        assertTrue(events.stream().filter(MusicEvent.TrackPlaybackChanged.class::isInstance)
                .map(MusicEvent.TrackPlaybackChanged.class::cast)
                .noneMatch(event -> event.state() == MusicEvent.PlaybackState.FINISHED));
    }

    @Test
    void queuedTrackKeepsItsRequestRoutingAndExistingLibraryUserData() throws Exception {
        AudioTrack first = load(wave("first.wav"));
        Path secondFile = wave("second.wav");
        AudioTrack second = load(secondFile);
        String oauthData = "{\"oauth-token\":\"fixture-token\"}";
        second.setUserData(oauthData);
        assertFalse(MusicCommandTraceContext.callWith(trace("first-command", "gateway-first"), () -> scheduler.queue(first)));
        assertTrue(MusicCommandTraceContext.callWith(trace("queued-command", "gateway-second"), () -> scheduler.queue(second)));
        Files.delete(secondFile);
        scheduler.nextTrack();

        MusicCommandResultEvent reply = replies.poll(5, TimeUnit.SECONDS);
        assertNotNull(reply);
        assertEquals("queued-command", reply.commandId());
        assertEquals("gateway-second", reply.targetNode());
        assertSame(oauthData, second.getUserData());
        MusicEvent.TrackLoadFailed failed = events.stream().filter(MusicEvent.TrackLoadFailed.class::isInstance)
                .map(MusicEvent.TrackLoadFailed.class::cast).findFirst().orElseThrow();
        assertEquals("queued-command", failed.correlationId());
        assertEquals(MusicEvent.TransitionSource.QUEUE, failed.source());
    }

    @Test
    void stoppedAndClearedTracksDoNotSendDelayedFailureReplies() throws Exception {
        AudioTrack first = load(wave("stopped.wav"));
        AudioTrack queued = load(wave("cleared.wav"));
        MusicCommandTraceContext.runWith(trace("stopped-command", "gateway"), () -> scheduler.queue(first));
        MusicCommandTraceContext.runWith(trace("cleared-command", "gateway"), () -> scheduler.queue(queued));
        scheduler.clearQueue();
        scheduler.onTrackException(player, queued, forbiddenFailure());
        scheduler.stop();
        scheduler.onTrackException(player, first, forbiddenFailure());
        assertNull(replies.poll());
    }

    @Test
    void failureMessagesClassifyForbiddenStreamsWithoutLeakingSignedUrls() throws Exception {
        AudioTrack track = load(wave("forbidden.wav"));
        MusicCommandTraceContext.runWith(trace("403-command", "gateway"), () -> scheduler.queue(track));
        scheduler.onTrackException(player, track, forbiddenFailure());
        scheduler.onTrackException(player, track, forbiddenFailure());
        MusicCommandResultEvent reply = replies.poll();
        assertNotNull(reply);
        assertTrue(reply.message().contains("HTTP 403"));
        assertFalse(reply.message().contains("googlevideo"));
        assertFalse(reply.message().contains("private-secret"));
        assertNull(replies.poll());
    }

    @Test
    void loadFailedEndWithoutExceptionUsesFallbackAndAdvancesTheQueueOnce() throws Exception {
        AudioTrack failing = load(wave("fallback-end.wav"));
        AudioTrack second = load(wave("next.wav"));
        MusicCommandTraceContext.runWith(trace("fallback-end-command", "gateway"), () -> scheduler.queue(failing));
        scheduler.queue(second);
        scheduler.onTrackEnd(player, failing, AudioTrackEndReason.LOAD_FAILED);
        assertEquals("fallback-end-command", replies.poll().commandId());
        assertSame(second, player.getPlayingTrack());
        assertFalse(queue.hasEntries(10L));
    }

    @Test
    void stuckTrackReportsFailureAndMovesToTheNextTrackWithoutSkippingIt() throws Exception {
        AudioTrack stuck = load(wave("stuck.wav"));
        AudioTrack next = load(wave("after-stuck.wav"));
        MusicCommandTraceContext.runWith(trace("stuck-command", "gateway"), () -> scheduler.queue(stuck));
        MusicCommandTraceContext.runWith(trace("next-command", "gateway-next"), () -> scheduler.queue(next));

        scheduler.onTrackStuck(player, stuck, 15000L);
        MusicCommandResultEvent reply = replies.poll();
        assertNotNull(reply);
        assertEquals("stuck-command", reply.commandId());
        assertSame(next, player.getPlayingTrack());
        assertFalse(queue.hasEntries(10L));
        scheduler.onTrackException(player, stuck, forbiddenFailure());
        scheduler.onTrackEnd(player, stuck, AudioTrackEndReason.STOPPED);
        scheduler.onTrackStuck(player, stuck, 15000L);
        assertSame(next, player.getPlayingTrack());
        assertNull(replies.poll());
    }

    @Test
    void aConcurrentReplacementBeforeTheStuckTransitionObtainsItsLockPreservesTheNewTrack() throws Exception {
        AudioTrack stuck = load(wave("concurrent-stuck.wav"));
        AudioTrack replacement = load(wave("replacement.wav"));
        AudioTrack queued = load(wave("still-queued.wav"));
        GuildPlaybackLockManager replacingLock = id -> {
            // Another transition replaces the track before this transition owns the guild lock.
            player.startTrack(replacement, false);
            return locks.tryAcquire(id);
        };
        TrackScheduler guardedScheduler = new TrackScheduler(10L, player, manager, queue, states, replacingLock,
                events::add, new MusicEventFactory(new AppProperties()), "audio-node-test", replies::add);
        player.removeListener(scheduler);
        player.addListener(guardedScheduler);
        MusicCommandTraceContext.runWith(trace("concurrent-stuck-command", "gateway"),
                () -> guardedScheduler.queue(stuck));
        guardedScheduler.queue(queued);

        guardedScheduler.onTrackStuck(player, stuck, 15000L);

        assertSame(replacement, player.getPlayingTrack());
        assertTrue(queue.hasEntries(10L));
        assertEquals(1, queue.list(10L, 10).size());
    }

    @Test
    void aTraceWithoutAnEnvelopeDoesNotInventAnInteractionReplyRoute() throws Exception {
        AudioTrack track = load(wave("without-envelope.wav"));
        MusicCommandTrace plainTrace = MusicCommandTrace.from(new MusicCommandMessage("plain-command", 1, 1L,
                "producer-is-not-response-target", new MusicCommand.Play(10L, 20L, 30L, "fixture", false)));
        assertNull(plainTrace.responseTargetNode());
        MusicCommandTraceContext.runWith(plainTrace, () -> scheduler.queue(track));
        scheduler.onTrackException(player, track, forbiddenFailure());
        assertNull(replies.poll());
        assertTrue(events.stream().filter(MusicEvent.TrackLoadFailed.class::isInstance)
                .anyMatch(event -> "plain-command".equals(event.correlationId())));
    }

    @Test
    void metadataAcknowledgmentDoesNotClaimThatAudioHasStarted() throws Exception {
        PlayerManager players = new PlayerManager(queue, states, locks, new AppProperties(), new YouTubeProperties(),
                events::add, new MusicEventFactory(new AppProperties()), replies::add);
        AudioManager audio = proxy(AudioManager.class, (method, args) -> null);
        Guild guild = proxy(Guild.class, (method, args) -> switch (method) {
            case "getIdLong" -> 10L;
            case "getAudioManager" -> audio;
            default -> null;
        });
        TextChannel channel = proxy(TextChannel.class, (method, args) -> "getGuild".equals(method) ? guild : null);
        var field = PlayerManager.class.getDeclaredField("audioPlayerManager");
        field.setAccessible(true);
        AudioPlayerManager createdManager = (AudioPlayerManager) field.get(players);
        try {
            String reply = players.loadAndPlay(channel, wave("accepted.wav").toString()).get(5, TimeUnit.SECONDS).message();
            assertTrue(reply.startsWith("재생 요청을 접수했습니다:"));
            assertFalse(reply.contains("재생을 시작했습니다"));
        } finally {
            createdManager.shutdown();
        }
    }

    private MusicCommandTrace trace(String commandId, String targetNode) {
        return MusicCommandTrace.from(new MusicCommandEnvelope(new MusicCommandMessage(commandId, 1, 1L,
                "origin-gateway", new MusicCommand.Play(10L, 20L, 30L, "fixture", false)),
                targetNode, MusicCommandResponseMode.EPHEMERAL));
    }

    private FriendlyException forbiddenFailure() {
        FriendlyException failure = new FriendlyException("All clients failed", FriendlyException.Severity.SUSPICIOUS, null);
        failure.addSuppressed(new IllegalStateException("Not success status code: 403 https://googlevideo.invalid/videoplayback?token=private-secret"));
        return failure;
    }

    private AudioTrack load(Path file) throws Exception {
        CompletableFuture<AudioTrack> loaded = new CompletableFuture<>();
        manager.loadItem(file.toString(), new AudioLoadResultHandler() {
            public void trackLoaded(AudioTrack track) { loaded.complete(track); }
            public void playlistLoaded(AudioPlaylist playlist) { loaded.completeExceptionally(new AssertionError("Unexpected playlist")); }
            public void noMatches() { loaded.completeExceptionally(new AssertionError("Missing fixture")); }
            public void loadFailed(FriendlyException error) { loaded.completeExceptionally(error); }
        });
        return loaded.get(5, TimeUnit.SECONDS);
    }

    private Path wave(String name) throws Exception {
        int sampleRate = 48000;
        int dataLength = sampleRate * 4;
        ByteBuffer wav = ByteBuffer.allocate(44 + dataLength).order(ByteOrder.LITTLE_ENDIAN);
        wav.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(36 + dataLength);
        wav.put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16).putShort((short) 1).putShort((short) 2);
        wav.putInt(sampleRate).putInt(sampleRate * 4).putShort((short) 4).putShort((short) 16);
        wav.put("data".getBytes(StandardCharsets.US_ASCII)).putInt(dataLength);
        for (int i = 0; i < sampleRate; i++) {
            short sample = (short) (8000 * Math.sin(2 * Math.PI * 440 * i / sampleRate));
            wav.putShort(sample).putShort(sample);
        }
        return Files.write(directory.resolve(name), wav.array());
    }

    private void consumeUntilEnded() throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (player.getPlayingTrack() != null && System.nanoTime() < deadline) {
            player.provide();
            Thread.sleep(5);
        }
        assertNull(player.getPlayingTrack());
    }

    @SuppressWarnings("unchecked")
    private <T> T proxy(Class<T> type, Invocation invocation) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (instance, method, args) -> invocation.call(method.getName(), args));
    }

    private interface Invocation { Object call(String method, Object[] args); }

    private static final class QueueStore implements QueueRepository {
        private final ConcurrentLinkedDeque<QueueEntry> entries = new ConcurrentLinkedDeque<>();
        public void push(long id, QueueEntry entry) { entries.add(entry); }
        public QueueEntry poll(long id) { return entries.poll(); }
        public boolean hasEntries(long id) { return !entries.isEmpty(); }
        public List<QueueEntry> list(long id, int limit) { return entries.stream().limit(limit).toList(); }
        public void clear(long id) { entries.clear(); }
    }

    private static final class StateStore implements PlayerStateRepository {
        private final PlayerState state = new PlayerState(10L);
        public PlayerState getOrCreate(long id) { return state; }
        public void save(PlayerState updated) { }
        public void remove(long id) { }
    }
}
