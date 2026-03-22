package discordgateway.playback.domain;

public interface PlayerStateRepository {
    PlayerState getOrCreate(long guildId);
    void save(PlayerState state);
    void remove(long guildId);
}
