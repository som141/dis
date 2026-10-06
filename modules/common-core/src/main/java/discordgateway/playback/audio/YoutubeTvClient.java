package discordgateway.playback.audio;

import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import dev.lavalink.youtube.clients.ClientConfig;
import dev.lavalink.youtube.clients.Tv;
import org.jetbrains.annotations.NotNull;

/** TV client compatibility fix until youtube-source releases its upstream change. */
public final class YoutubeTvClient extends Tv {

    // youtube-source 1.18.2 still uses the Cobalt UA rejected by the TVHTML5 endpoint.
    // Backport: https://github.com/lavalink-devs/youtube-source/commit/b33460b38ad13b5cd07da75e46444397cf0ea2df
    private static final String USER_AGENT =
            "Mozilla/5.0 (PlayStation; PlayStation 4/12.00) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.0 Safari/605.1.15";

    @Override
    @NotNull
    protected ClientConfig getBaseClientConfig(@NotNull HttpInterface httpInterface) {
        return super.getBaseClientConfig(httpInterface).withUserAgent(USER_AGENT);
    }
}
