package discordgateway.stocknode.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "stock.messaging")
public class StockNodeMessagingProperties {

    private String commandExchange = "stock.command.exchange";
    private String commandQueue = "stock.command.queue";
    private String commandRoutingKey = "stock.command";
    private String commandResultExchange = "stock.command.result.exchange";
    private String commandResultRoutingKeyPrefix = "stock.command.result.";

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

    public String commandResultRoutingKey(String nodeName) {
        return commandResultRoutingKeyPrefix + nodeName;
    }
}
