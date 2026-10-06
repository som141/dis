package discordgateway.playback.audio;

import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import dev.lavalink.youtube.YoutubeAudioSourceManager;
import dev.lavalink.youtube.YoutubeSourceOptions;
import dev.lavalink.youtube.clients.ClientConfig;
import dev.lavalink.youtube.clients.MWeb;
import dev.lavalink.youtube.clients.Tv;
import dev.lavalink.youtube.clients.skeleton.Client;
import dev.lavalink.youtube.http.YoutubeHttpContextFilter;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.HttpClientContext;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static dev.lavalink.youtube.http.YoutubeOauth2Handler.OAUTH_INJECT_CONTEXT_ATTRIBUTE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YoutubeTvClientTest {

    @Test
    void playerRequestUsesUpstreamTvUserAgentAndKeepsOauthWithoutChangingSharedClientConfiguration() throws IOException {
        String originalTvUserAgent = Tv.BASE_CONFIG.getUserAgent();
        String originalMWebConfig = MWeb.BASE_CONFIG.toJsonString();
        String originalMWebUserAgent = MWeb.BASE_CONFIG.getUserAgent();
        YoutubeTvClient client = new YoutubeTvClient();
        YoutubeAudioSourceManager source = new YoutubeAudioSourceManager(new YoutubeSourceOptions(), client);

        try (HttpInterface httpInterface = source.getInterface()) {
            ClientConfig config = client.getBaseClientConfig(httpInterface);
            assertEquals("TVHTML5", client.getIdentifier());
            assertTrue(client.supportsOAuth());
            assertEquals(Tv.BASE_CONFIG.toJsonString(), config.toJsonString());

            config.setAttributes(httpInterface);
            HttpClientContext context = httpInterface.getContext();
            context.setAttribute(YoutubeHttpContextFilter.ATTRIBUTE_VISITOR_DATA_SPECIFIED, "fixture-visitor-data");
            context.setAttribute(Client.OAUTH_CLIENT_ATTRIBUTE, Boolean.TRUE);
            context.setAttribute(OAUTH_INJECT_CONTEXT_ATTRIBUTE, "fixture-access-token");

            HttpPost playerRequest = new HttpPost(Client.PLAYER_URL);
            source.getContextFilter().onRequest(context, playerRequest, false);

            assertEquals(
                    "Mozilla/5.0 (PlayStation; PlayStation 4/12.00) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.0 Safari/605.1.15",
                    playerRequest.getFirstHeader("User-Agent").getValue());
            assertFalse(playerRequest.getFirstHeader("User-Agent").getValue().contains("Cobalt"));
            assertEquals("Bearer fixture-access-token", playerRequest.getFirstHeader("Authorization").getValue());
            assertEquals("fixture-visitor-data", playerRequest.getFirstHeader("X-Goog-Visitor-Id").getValue());
            assertNull(context.getAttribute(Client.OAUTH_CLIENT_ATTRIBUTE));

            assertEquals(originalTvUserAgent, Tv.BASE_CONFIG.getUserAgent());
            assertEquals(originalMWebConfig, MWeb.BASE_CONFIG.toJsonString());
            assertEquals(originalMWebUserAgent, MWeb.BASE_CONFIG.getUserAgent());
        } finally {
            source.shutdown();
        }
    }
}
