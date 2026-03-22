package discordgateway.common.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "messaging")
public class MessagingProperties {

    private String commandExchange = "music.command.exchange";
    private String commandQueue = "music.command.queue";
    private String commandRoutingKey = "music.command";
    private String commandDeadLetterExchange = "music.command.dlx";
    private String commandDeadLetterQueue = "music.command.dlq";
    private String commandDeadLetterRoutingKey = "music.command.dead";
    private String commandResultExchange = "music.command.result.exchange";
    private String commandResultRoutingKeyPrefix = "music.command.result.";
    private String commandResultQueuePrefix = "music.command.result.";
    private long commandDedupTtlMs = 300_000L;

    public String getCommandExchange() {
        return commandExchange;
    }

    public void setCommandExchange(String commandExchange) {
        this.commandExchange = commandExchange;
    }

    public String getCommandQueue() {
        return commandQueue;
    }

    public void setCommandQueue(String commandQueue) {
        this.commandQueue = commandQueue;
    }

    public String getCommandRoutingKey() {
        return commandRoutingKey;
    }

    public void setCommandRoutingKey(String commandRoutingKey) {
        this.commandRoutingKey = commandRoutingKey;
    }

    public String getCommandDeadLetterExchange() {
        return commandDeadLetterExchange;
    }

    public void setCommandDeadLetterExchange(String commandDeadLetterExchange) {
        this.commandDeadLetterExchange = commandDeadLetterExchange;
    }

    public String getCommandDeadLetterQueue() {
        return commandDeadLetterQueue;
    }

    public void setCommandDeadLetterQueue(String commandDeadLetterQueue) {
        this.commandDeadLetterQueue = commandDeadLetterQueue;
    }

    public String getCommandDeadLetterRoutingKey() {
        return commandDeadLetterRoutingKey;
    }

    public void setCommandDeadLetterRoutingKey(String commandDeadLetterRoutingKey) {
        this.commandDeadLetterRoutingKey = commandDeadLetterRoutingKey;
    }

    public String getCommandResultExchange() {
        return commandResultExchange;
    }

    public void setCommandResultExchange(String commandResultExchange) {
        this.commandResultExchange = commandResultExchange;
    }

    public String getCommandResultRoutingKeyPrefix() {
        return commandResultRoutingKeyPrefix;
    }

    public void setCommandResultRoutingKeyPrefix(String commandResultRoutingKeyPrefix) {
        this.commandResultRoutingKeyPrefix = commandResultRoutingKeyPrefix;
    }

    public String getCommandResultQueuePrefix() {
        return commandResultQueuePrefix;
    }

    public void setCommandResultQueuePrefix(String commandResultQueuePrefix) {
        this.commandResultQueuePrefix = commandResultQueuePrefix;
    }

    public long getCommandDedupTtlMs() {
        return commandDedupTtlMs;
    }

    public void setCommandDedupTtlMs(long commandDedupTtlMs) {
        this.commandDedupTtlMs = commandDedupTtlMs;
    }

    public String commandResultRoutingKey(String nodeName) {
        return commandResultRoutingKeyPrefix + nodeName;
    }

    public String commandResultQueue(String nodeName) {
        return commandResultQueuePrefix + nodeName + ".queue";
    }
}
