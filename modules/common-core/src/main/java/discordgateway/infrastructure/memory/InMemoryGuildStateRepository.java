package discordgateway.infrastructure.memory;

import discordgateway.domain.GuildPlayerState;
import discordgateway.domain.GuildStateRepository;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryGuildStateRepository implements GuildStateRepository {

    private final Map<Long, GuildPlayerState> store = new ConcurrentHashMap<>();

    @Override
    public GuildPlayerState getOrCreate(long guildId) {
        return store.computeIfAbsent(guildId, GuildPlayerState::new);
    }

    @Override
    public void save(GuildPlayerState state) {
        store.put(state.getGuildId(), state);
    }

    @Override
    public void remove(long guildId) {
        store.remove(guildId);
    }
}