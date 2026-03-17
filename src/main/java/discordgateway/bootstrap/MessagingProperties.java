package discordgateway.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "messaging")
public class MessagingProperties {

    private String commandTransport = "in-process";
    private String eventTransport = "spring";
    private long rpcTimeoutMs = 10_000L;
    private String commandExchange = "music.command.exchange";
    private String commandQueue = "music.command.queue";
    private String commandRoutingKey = "music.command";
    private String commandDeadLetterExchange = "music.command.dlx";
    private String commandDeadLetterQueue = "music.command.dlq";
    private String commandDeadLetterRoutingKey = "music.command.dead";
    private long commandDedupTtlMs = 300_000L;
    private String eventExchange = "music.event.exchange";
    private String eventRoutingKeyPrefix = "guild";
    private long eventPublishConfirmTimeoutMs = 5_000L;
    private long eventOutboxFlushIntervalMs = 5_000L;
    private long eventOutboxClaimTtlMs = 30_000L;
    private long eventOutboxRetryDelayMs = 10_000L;
    private int eventOutboxBatchSize = 50;

    public String getCommandTransport() {
        return commandTransport;
    }

    public void setCommandTransport(String commandTransport) {
        this.commandTransport = commandTransport;
    }

    public String getEventTransport() {
        return eventTransport;
    }

    public void setEventTransport(String eventTransport) {
        this.eventTransport = eventTransport;
    }

    public long getRpcTimeoutMs() {
        return rpcTimeoutMs;
    }

    public void setRpcTimeoutMs(long rpcTimeoutMs) {
        this.rpcTimeoutMs = rpcTimeoutMs;
    }

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

    public long getCommandDedupTtlMs() {
        return commandDedupTtlMs;
    }

    public void setCommandDedupTtlMs(long commandDedupTtlMs) {
        this.commandDedupTtlMs = commandDedupTtlMs;
    }

    public String getEventExchange() {
        return eventExchange;
    }

    public void setEventExchange(String eventExchange) {
        this.eventExchange = eventExchange;
    }

    public String getEventRoutingKeyPrefix() {
        return eventRoutingKeyPrefix;
    }

    public void setEventRoutingKeyPrefix(String eventRoutingKeyPrefix) {
        this.eventRoutingKeyPrefix = eventRoutingKeyPrefix;
    }

    public long getEventPublishConfirmTimeoutMs() {
        return eventPublishConfirmTimeoutMs;
    }

    public void setEventPublishConfirmTimeoutMs(long eventPublishConfirmTimeoutMs) {
        this.eventPublishConfirmTimeoutMs = eventPublishConfirmTimeoutMs;
    }

    public long getEventOutboxFlushIntervalMs() {
        return eventOutboxFlushIntervalMs;
    }

    public void setEventOutboxFlushIntervalMs(long eventOutboxFlushIntervalMs) {
        this.eventOutboxFlushIntervalMs = eventOutboxFlushIntervalMs;
    }

    public long getEventOutboxClaimTtlMs() {
        return eventOutboxClaimTtlMs;
    }

    public void setEventOutboxClaimTtlMs(long eventOutboxClaimTtlMs) {
        this.eventOutboxClaimTtlMs = eventOutboxClaimTtlMs;
    }

    public long getEventOutboxRetryDelayMs() {
        return eventOutboxRetryDelayMs;
    }

    public void setEventOutboxRetryDelayMs(long eventOutboxRetryDelayMs) {
        this.eventOutboxRetryDelayMs = eventOutboxRetryDelayMs;
    }

    public int getEventOutboxBatchSize() {
        return eventOutboxBatchSize;
    }

    public void setEventOutboxBatchSize(int eventOutboxBatchSize) {
        this.eventOutboxBatchSize = eventOutboxBatchSize;
    }
}
