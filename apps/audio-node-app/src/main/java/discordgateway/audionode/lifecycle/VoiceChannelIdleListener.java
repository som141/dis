package discordgateway.audionode.lifecycle;

import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class VoiceChannelIdleListener extends ListenerAdapter {

    private final VoiceChannelIdleDisconnectService idleDisconnectService;

    public VoiceChannelIdleListener(VoiceChannelIdleDisconnectService idleDisconnectService) {
        this.idleDisconnectService = idleDisconnectService;
    }

    @Override
    public void onGuildVoiceUpdate(GuildVoiceUpdateEvent event) {
        idleDisconnectService.evaluate(event.getGuild(), "guild-voice-update");
    }
}
