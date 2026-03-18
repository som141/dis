package discordgateway.domain;

public interface GuildStateRepository {
    GuildPlayerState getOrCreate(long guildId);
    void save(GuildPlayerState state);
    void remove(long guildId);
}