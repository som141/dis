package discordgateway.domain;

public class GuildPlayerState {
    private final long guildId;
    private boolean autoPlay;
    private Long connectedVoiceChannelId;

    public GuildPlayerState(long guildId) {
        this.guildId = guildId;
    }

    public long getGuildId() {
        return guildId;
    }

    public boolean isAutoPlay() {
        return autoPlay;
    }

    public void setAutoPlay(boolean autoPlay) {
        this.autoPlay = autoPlay;
    }

    public Long getConnectedVoiceChannelId() {
        return connectedVoiceChannelId;
    }

    public void setConnectedVoiceChannelId(Long connectedVoiceChannelId) {
        this.connectedVoiceChannelId = connectedVoiceChannelId;
    }
}