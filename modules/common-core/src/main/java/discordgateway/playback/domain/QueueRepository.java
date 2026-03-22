package discordgateway.playback.domain;

import java.util.List;

public interface QueueRepository {
    void push(long guildId, QueueEntry entry);
    QueueEntry poll(long guildId);
    boolean hasEntries(long guildId);
    List<QueueEntry> list(long guildId, int limit);
    void clear(long guildId);
}
