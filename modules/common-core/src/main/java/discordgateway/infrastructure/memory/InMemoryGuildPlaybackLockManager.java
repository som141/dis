package discordgateway.infrastructure.memory;

import discordgateway.domain.GuildPlaybackLockManager;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public class InMemoryGuildPlaybackLockManager implements GuildPlaybackLockManager {

    private final ConcurrentHashMap<Long, ReentrantLock> locks = new ConcurrentHashMap<>();

    @Override
    public GuildPlaybackLock tryAcquire(long guildId) {
        ReentrantLock lock = locks.computeIfAbsent(guildId, ignored -> new ReentrantLock());
        if (!lock.tryLock()) {
            return new FailedLock();
        }
        return new HeldLock(lock);
    }

    private static final class HeldLock implements GuildPlaybackLock {
        private final ReentrantLock lock;
        private boolean released;

        private HeldLock(ReentrantLock lock) {
            this.lock = lock;
        }

        @Override
        public boolean acquired() {
            return true;
        }

        @Override
        public void release() {
            if (released) {
                return;
            }
            released = true;
            lock.unlock();
        }
    }

    private static final class FailedLock implements GuildPlaybackLock {
        @Override
        public boolean acquired() {
            return false;
        }

        @Override
        public void release() {
        }
    }
}
