package discordgateway.infrastructure.memory;

import discordgateway.domain.PlayerState;
import discordgateway.domain.PlayerStateRepository;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryPlayerStateRepository implements PlayerStateRepository {

    private final Map<Long, PlayerState> store = new ConcurrentHashMap<>();

    @Override
    public PlayerState getOrCreate(long guildId) {
        return store.computeIfAbsent(guildId, PlayerState::new);
    }

    @Override
    public void save(PlayerState state) {
        store.put(state.getGuildId(), state);
    }

    @Override
    public void remove(long guildId) {
        store.remove(guildId);
    }
}
