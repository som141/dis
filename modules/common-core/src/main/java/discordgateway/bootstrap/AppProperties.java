package discordgateway.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private long guildLockTtlMs = 15_000L;
    private String nodeName = "discord-node";

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
