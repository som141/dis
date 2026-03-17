package discordgateway.infrastructure.memory;

import discordgateway.domain.QueueEntry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class InMemoryQueueRepositoryTest {

    @Test
    void pushListPollAndClearQueueInOrder() {
        InMemoryQueueRepository repository = new InMemoryQueueRepository();
        QueueEntry first = new QueueEntry("id-1", "first", "author-a", 1L);
        QueueEntry second = new QueueEntry("id-2", "second", "author-b", 2L);

        repository.push(100L, first);
        repository.push(100L, second);

        List<QueueEntry> listed = repository.list(100L, 10);
        assertEquals(List.of(first, second), listed);
        assertEquals(first, repository.poll(100L));
        assertEquals(second, repository.poll(100L));
        assertNull(repository.poll(100L));

        repository.push(100L, first);
        repository.clear(100L);
        assertEquals(List.of(), repository.list(100L, 10));
    }
}
