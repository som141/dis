package discordgateway.domain;

public class GuildPlayerState {
    private final long guildId;
    private Long connectedVoiceChannelId;
    private Long lastTextChannelId;

    public GuildPlayerState(long guildId) {
        this.guildId = guildId;
    }

    public long getGuildId() {
        return guildId;
    }

    public Long getConnectedVoiceChannelId() {
        return connectedVoiceChannelId;
    }

    public void setConnectedVoiceChannelId(Long connectedVoiceChannelId) {
        this.connectedVoiceChannelId = connectedVoiceChannelId;
    }

    public Long getLastTextChannelId() {
        return lastTextChannelId;
    }

    public void setLastTextChannelId(Long lastTextChannelId) {
        this.lastTextChannelId = lastTextChannelId;
    }
}
