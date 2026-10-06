package discordgateway.gateway.messaging;

import discordgateway.common.command.MusicCommandResultEvent;
import discordgateway.gateway.interaction.InteractionResponseContext;
import discordgateway.gateway.interaction.PendingInteractionRepository;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RabbitMusicCommandResultListenerTest {

    @Test
    void acceptedPlaybackRetainsItsOriginalReplyAndLaterFailureEditsItOnce() {
        Store pending = new Store();
        InteractionResponseContext context = context("play");
        pending.put("command", context);
        List<String> messages = new ArrayList<>();
        RabbitMusicCommandResultListener listener = listener(pending, messages, context);

        listener.handle(result(true, "SUCCESS", "재생 요청을 접수했습니다"));
        assertSame(context, pending.find("command"));
        listener.handle(result(false, "PLAYBACK_FAILED", "음원 접근 실패"));
        assertNull(pending.find("command"));
        listener.handle(result(true, "DUPLICATE_REPLAY", "재생 요청을 접수했습니다"));
        listener.handle(result(false, "PLAYBACK_FAILED", "음원 접근 실패"));

        assertEquals(List.of("재생 요청을 접수했습니다", "음원 접근 실패"), messages);
    }

    @Test
    void failureDeliveredBeforeMetadataAcknowledgmentCannotBeOverwrittenByTheAcknowledgment() {
        Store pending = new Store();
        InteractionResponseContext context = context("play");
        pending.put("command", context);
        List<String> messages = new ArrayList<>();
        RabbitMusicCommandResultListener listener = listener(pending, messages, context);

        listener.handle(result(false, "PLAYBACK_FAILED", "음원 접근 실패"));
        listener.handle(result(true, "SUCCESS", "재생 요청을 접수했습니다"));
        listener.handle(result(true, "DUPLICATE_REPLAY", "재생 요청을 접수했습니다"));

        assertEquals(List.of("음원 접근 실패"), messages);
        assertNull(pending.find("command"));
    }

    @Test
    void queuedEffectRetainsTheInteractionForItsLaterPlaybackFailure() {
        Store pending = new Store();
        InteractionResponseContext context = context("sfx");
        pending.put("command", context);
        List<String> messages = new ArrayList<>();
        RabbitMusicCommandResultListener listener = listener(pending, messages, context);

        listener.handle(result(true, "SUCCESS", "대기열에 추가했습니다"));
        listener.handle(result(false, "PLAYBACK_FAILED", "재생에 실패했습니다"));

        assertEquals(List.of("대기열에 추가했습니다", "재생에 실패했습니다"), messages);
        assertNull(pending.find("command"));
    }

    @Test
    void nonPlaybackCommandStillConsumesItsSingleResponse() {
        Store pending = new Store();
        InteractionResponseContext context = context("skip");
        pending.put("command", context);
        List<String> messages = new ArrayList<>();
        RabbitMusicCommandResultListener listener = listener(pending, messages, context);

        listener.handle(result(true, "SUCCESS", "다음 곡으로 건너뜁니다"));
        listener.handle(result(true, "DUPLICATE_REPLAY", "다음 곡으로 건너뜁니다"));

        assertEquals(List.of("다음 곡으로 건너뜁니다"), messages);
        assertNull(pending.find("command"));
    }

    @Test
    void missingOrExpiredInteractionDoesNotCreateANewPublicReply() {
        Store pending = new Store();
        List<String> messages = new ArrayList<>();
        RabbitMusicCommandResultListener listener = listener(pending, messages, null);
        listener.handle(result(false, "PLAYBACK_FAILED", "재생 실패"));
        pending.put("command", new InteractionResponseContext("expired-fixture-token", "play", 10L, 20L, 1L, 2L));
        listener.handle(result(false, "PLAYBACK_FAILED", "재생 실패"));
        assertTrue(messages.isEmpty());
    }

    private RabbitMusicCommandResultListener listener(Store pending, List<String> messages,
                                                      InteractionResponseContext expectedContext) {
        return new RabbitMusicCommandResultListener(pending, (context, id, guild, type, message) -> {
            assertSame(expectedContext, context);
            assertEquals("command", id);
            messages.add(message);
        });
    }

    private InteractionResponseContext context(String commandName) {
        long now = System.currentTimeMillis();
        return new InteractionResponseContext("fixture-token", commandName, 10L, 20L, now, now + 900000L);
    }

    private MusicCommandResultEvent result(boolean success, String type, String message) {
        return new MusicCommandResultEvent("command", 1, 1L, "audio", "gateway", 10L, success, message, true, type);
    }

    private static final class Store implements PendingInteractionRepository {
        private final Map<String, InteractionResponseContext> records = new HashMap<>();
        public void put(String commandId, InteractionResponseContext context) { records.put(commandId, context); }
        public InteractionResponseContext find(String commandId) {
            InteractionResponseContext context = records.get(commandId);
            return context == null || context.isExpired(System.currentTimeMillis()) ? null : context;
        }
        public InteractionResponseContext take(String commandId) {
            InteractionResponseContext context = find(commandId);
            records.remove(commandId);
            return context;
        }
        public void remove(String commandId) { records.remove(commandId); }
    }
}
