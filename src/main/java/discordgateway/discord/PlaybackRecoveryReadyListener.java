package discordgateway.discord;

import discordgateway.application.PlaybackRecoveryService;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

public class PlaybackRecoveryReadyListener extends ListenerAdapter {

    private final PlaybackRecoveryService playbackRecoveryService;

    public PlaybackRecoveryReadyListener(PlaybackRecoveryService playbackRecoveryService) {
        this.playbackRecoveryService = playbackRecoveryService;
    }

    @Override
    public void onReady(@NotNull ReadyEvent event) {
        playbackRecoveryService.recoverAll(event.getJDA().getGuilds());
    }
}
