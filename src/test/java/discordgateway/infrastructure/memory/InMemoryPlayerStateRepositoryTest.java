package discordgateway.infrastructure.memory;

import discordgateway.domain.PlayerState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryPlayerStateRepositoryTest {

    @Test
    void saveAndLoadState() {
        InMemoryPlayerStateRepository repository = new InMemoryPlayerStateRepository();

        PlayerState state = repository.getOrCreate(10L);
        state.setNowPlaying("yt-track-id");
        state.setPaused(true);
        state.setAutoPlay(true);
        state.setRepeatMode("OFF");
        state.setOwnerNode("node-a");
        state.setProcessingFlag(true);

        repository.save(state);

        PlayerState loaded = repository.getOrCreate(10L);
        assertEquals("yt-track-id", loaded.getNowPlaying());
        assertTrue(loaded.isPaused());
        assertTrue(loaded.isAutoPlay());
        assertEquals("OFF", loaded.getRepeatMode());
        assertEquals("node-a", loaded.getOwnerNode());
        assertTrue(loaded.isProcessingFlag());
    }

    @Test
    void removeClearsStoredState() {
        InMemoryPlayerStateRepository repository = new InMemoryPlayerStateRepository();
        PlayerState state = repository.getOrCreate(20L);
        state.setNowPlaying("track");
        repository.save(state);

        repository.remove(20L);

        PlayerState loaded = repository.getOrCreate(20L);
        assertNull(loaded.getNowPlaying());
        assertFalse(loaded.isPaused());
        assertFalse(loaded.isAutoPlay());
        assertEquals("OFF", loaded.getRepeatMode());
        assertNull(loaded.getOwnerNode());
        assertFalse(loaded.isProcessingFlag());
    }
}
