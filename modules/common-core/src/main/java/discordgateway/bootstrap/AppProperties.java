package discordgateway.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private AppRole role = AppRole.ALL;
    private String stateStore = "memory";
    private String queueStore;
    private String playerStateStore;
    private String commandDedupStore;
    private String eventOutboxStore;
    private long guildLockTtlMs = 15_000L;
    private String nodeName = "discord-gateway";

    public AppRole getRole() {
        return role;
    }

    public void setRole(AppRole role) {
        this.role = role;
    }

    public String getStateStore() {
        return stateStore;
    }

    public void setStateStore(String stateStore) {
        this.stateStore = stateStore;
    }

    public String getQueueStore() {
        return queueStore == null || queueStore.isBlank() ? stateStore : queueStore;
    }

    public void setQueueStore(String queueStore) {
        this.queueStore = queueStore;
    }

    public String getPlayerStateStore() {
        return playerStateStore == null || playerStateStore.isBlank() ? stateStore : playerStateStore;
    }

    public void setPlayerStateStore(String playerStateStore) {
        this.playerStateStore = playerStateStore;
    }

    public String getCommandDedupStore() {
        return commandDedupStore == null || commandDedupStore.isBlank() ? stateStore : commandDedupStore;
    }

    public void setCommandDedupStore(String commandDedupStore) {
        this.commandDedupStore = commandDedupStore;
    }

    public String getEventOutboxStore() {
        return eventOutboxStore == null || eventOutboxStore.isBlank() ? stateStore : eventOutboxStore;
    }

    public void setEventOutboxStore(String eventOutboxStore) {
        this.eventOutboxStore = eventOutboxStore;
    }

    public long getGuildLockTtlMs() {
        return guildLockTtlMs;
    }

    public void setGuildLockTtlMs(long guildLockTtlMs) {
        this.guildLockTtlMs = guildLockTtlMs;
    }

    public String getNodeName() {
        return nodeName;
    }

    public void setNodeName(String nodeName) {
        this.nodeName = nodeName;
    }
}
