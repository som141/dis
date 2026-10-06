package discordgateway.playback.audio;

import dev.lavalink.youtube.YoutubeAudioSourceManager;
import dev.lavalink.youtube.YoutubeSourceOptions;
import dev.lavalink.youtube.clients.Tv;
import dev.lavalink.youtube.clients.skeleton.Client;
import dev.lavalink.youtube.http.YoutubeHttpContextFilter;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.HttpClientContext;
import org.junit.jupiter.api.Test;

import static dev.lavalink.youtube.http.YoutubeOauth2Handler.OAUTH_INJECT_CONTEXT_ATTRIBUTE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class YoutubeOAuthHttpContextTest {

    @Test
    void keepsOauthContextUntilThePlayerRequestAndDoesNotLeakAuthenticationToLaterRequests() {
        YoutubeAudioSourceManager source = new YoutubeAudioSourceManager(new YoutubeSourceOptions(), new Tv());
        try {
            YoutubeHttpContextFilter filter = source.getContextFilter();
            HttpClientContext context = HttpClientContext.create();
            context.setAttribute(Client.OAUTH_CLIENT_ATTRIBUTE, Boolean.TRUE);
            context.setAttribute(OAUTH_INJECT_CONTEXT_ATTRIBUTE, "fixture-access-token");

            // The player JavaScript can be fetched before the /player request in the same HTTP context.
            HttpGet scriptRequest = new HttpGet("https://www.youtube.com/s/player/fixture/base.js");
            filter.onRequest(context, scriptRequest, false);
            assertEquals(Boolean.TRUE, context.getAttribute(Client.OAUTH_CLIENT_ATTRIBUTE));
            assertNull(scriptRequest.getFirstHeader("Authorization"));

            HttpPost playerRequest = new HttpPost(Client.PLAYER_URL);
            filter.onRequest(context, playerRequest, false);
            assertEquals("Bearer fixture-access-token", playerRequest.getFirstHeader("Authorization").getValue());
            assertNull(context.getAttribute(Client.OAUTH_CLIENT_ATTRIBUTE));

            HttpPost laterRequest = new HttpPost(Client.PLAYER_URL);
            filter.onRequest(context, laterRequest, false);
            assertNull(laterRequest.getFirstHeader("Authorization"));
        } finally {
            source.shutdown();
        }
    }
}
