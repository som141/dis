package discordgateway.infrastructure.memory;

import discordgateway.domain.QueueEntry;
import discordgateway.domain.QueueRepository;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

public class InMemoryQueueRepository implements QueueRepository {

    private final ConcurrentHashMap<Long, Deque<QueueEntry>> store = new ConcurrentHashMap<>();

    @Override
    public void push(long guildId, QueueEntry entry) {
        store.computeIfAbsent(guildId, id -> new ConcurrentLinkedDeque<>()).addLast(entry);
    }

    @Override
    public QueueEntry poll(long guildId) {
        Deque<QueueEntry> deque = store.get(guildId);
        if (deque == null) {
            return null;
        }
        return deque.pollFirst();
    }

    @Override
    public List<QueueEntry> list(long guildId, int limit) {
        Deque<QueueEntry> deque = store.get(guildId);
        if (deque == null || deque.isEmpty()) {
            return List.of();
        }

        List<QueueEntry> result = new ArrayList<>();
        int count = 0;
        for (QueueEntry entry : deque) {
            result.add(entry);
            count++;
            if (count >= limit) {
                break;
            }
        }
        return result;
    }

    @Override
    public void clear(long guildId) {
        store.remove(guildId);
    }
}