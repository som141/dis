package discordgateway.domain;

public interface GuildPlaybackLockManager {

    GuildPlaybackLock tryAcquire(long guildId);

    interface GuildPlaybackLock extends AutoCloseable {
        boolean acquired();

        void release();

        @Override
        default void close() {
            release();
        }
    }
}
