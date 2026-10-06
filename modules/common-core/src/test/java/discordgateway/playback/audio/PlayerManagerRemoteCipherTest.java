package discordgateway.playback.audio;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.lavalink.youtube.YoutubeAudioSourceManager;
import dev.lavalink.youtube.YoutubeSource;
import dev.lavalink.youtube.track.format.StreamFormat;
import discordgateway.common.bootstrap.AppProperties;
import discordgateway.common.bootstrap.YouTubeProperties;
import discordgateway.common.event.MusicEventFactory;
import org.apache.http.entity.ContentType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerManagerRemoteCipherTest {

    private static final String PLAYER_SCRIPT = "https://www.youtube.com/s/player/fixture/base.js";
    private static final String STREAM_URL = "https://example.invalid/videoplayback?n=encrypted-n";
    private static final String RESOLVED_URL = "https://example.invalid/videoplayback?n=resolved-n&sig=resolved-signature";
    private static final String CIPHER_PASSWORD = "test-cipher-password";
    private static final String CIPHER_USER_AGENT = "dis-remote-cipher-test";

    private final ConcurrentLinkedQueue<CipherRequest> requests = new ConcurrentLinkedQueue<>();
    private HttpServer server;
    private AudioPlayerManager audioPlayerManager;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
    }

    @AfterEach
    void closeResources() {
        if (audioPlayerManager != null) {
            audioPlayerManager.shutdown();
        }
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void configuredCipherResolvesStreamUrlAndFetchesTimestampThroughTheRemoteService() throws Exception {
        server.createContext("/resolve_url", exchange -> respond(exchange, 200,
                "{\"resolved_url\":\"" + RESOLVED_URL + "\"}"));
        server.createContext("/get_sts", exchange -> respond(exchange, 200, "{\"sts\":20400}"));
        YoutubeAudioSourceManager source = configuredSource();
        assertNotNull(source.getRemoteCipherManager());

        try (HttpInterface httpInterface = source.getInterface()) {
            URI resolved = source.getCipherManager().resolveFormatUrl(httpInterface, PLAYER_SCRIPT, streamFormat());
            assertEquals(URI.create(RESOLVED_URL), resolved);
            assertEquals("20400", source.getCipherManager().getTimestamp(httpInterface, PLAYER_SCRIPT));
        }

        assertEquals(2, requests.size());
        CipherRequest resolveRequest = requests.poll();
        assertRequestHeaders(resolveRequest, "/resolve_url");
        JsonBrowser resolveBody = JsonBrowser.parse(resolveRequest.body());
        assertEquals(STREAM_URL, resolveBody.get("stream_url").text());
        assertEquals(PLAYER_SCRIPT, resolveBody.get("player_url").text());
        assertEquals("encrypted-signature", resolveBody.get("encrypted_signature").text());
        assertEquals("encrypted-n", resolveBody.get("n_param").text());
        assertEquals("sig", resolveBody.get("signature_key").text());

        CipherRequest timestampRequest = requests.poll();
        assertRequestHeaders(timestampRequest, "/get_sts");
        assertEquals(PLAYER_SCRIPT, JsonBrowser.parse(timestampRequest.body()).get("player_url").text());
    }

    @Test
    void remoteCipherAuthenticationFailureStopsStreamResolution() throws Exception {
        server.createContext("/resolve_url", exchange -> respond(exchange, 401, "{\"error\":\"Invalid API token\"}"));
        YoutubeAudioSourceManager source = configuredSource();

        try (HttpInterface httpInterface = source.getInterface()) {
            IOException failure = assertThrows(IOException.class,
                    () -> source.getCipherManager().resolveFormatUrl(httpInterface, PLAYER_SCRIPT, streamFormat()));
            assertTrue(failure.getMessage().contains("401"));
        }

        assertEquals(1, requests.size());
        assertRequestHeaders(requests.poll(), "/resolve_url");
    }

    private YoutubeAudioSourceManager configuredSource() throws ReflectiveOperationException {
        YouTubeProperties properties = new YouTubeProperties();
        properties.setRemoteCipherUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setRemoteCipherPassword(CIPHER_PASSWORD);
        properties.setRemoteCipherUserAgent(CIPHER_USER_AGENT);
        AppProperties appProperties = new AppProperties();

        // These repositories are only used when creating a guild player; this test exercises source configuration.
        PlayerManager playerManager = new PlayerManager(null, null, null, appProperties, properties,
                event -> { }, new MusicEventFactory(appProperties));
        Field managerField = PlayerManager.class.getDeclaredField("audioPlayerManager");
        managerField.setAccessible(true);
        audioPlayerManager = (AudioPlayerManager) managerField.get(playerManager);
        YoutubeAudioSourceManager source = audioPlayerManager.source(YoutubeAudioSourceManager.class);
        assertNotNull(source);
        return source;
    }

    private StreamFormat streamFormat() {
        return new StreamFormat(ContentType.parse("audio/webm; codecs=opus"), 251, 128000, 1000, 2,
                STREAM_URL, "encrypted-n", "encrypted-signature", "sig", true, false);
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        try (exchange) {
            requests.add(new CipherRequest(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                    exchange.getRequestHeaders().getFirst("Authorization"),
                    exchange.getRequestHeaders().getFirst("User-Agent"),
                    exchange.getRequestHeaders().getFirst("Plugin-Version"),
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
            byte[] response = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, response.length);
            exchange.getResponseBody().write(response);
        }
    }

    private void assertRequestHeaders(CipherRequest request, String expectedPath) {
        assertNotNull(request);
        assertEquals("POST", request.method());
        assertEquals(expectedPath, request.path());
        assertEquals(CIPHER_PASSWORD, request.authorization());
        assertEquals(CIPHER_USER_AGENT, request.userAgent());
        assertEquals(YoutubeSource.VERSION, request.pluginVersion());
    }

    private record CipherRequest(String method, String path, String authorization, String userAgent,
                                 String pluginVersion, String body) {
    }
}
