package discordgateway.stocknode.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "stock.provider")
public class StockProviderProperties {

    private String type = "mock";
    private boolean fallbackToMock = true;
    private Duration timeout = Duration.ofSeconds(3);
    private String alphaVantageBaseUrl = "https://www.alphavantage.co";
    private String alphaVantageApiKey = "";
    private String alphaVantageEntitlement = "";

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public boolean isFallbackToMock() {
        return fallbackToMock;
    }

    public void setFallbackToMock(boolean fallbackToMock) {
        this.fallbackToMock = fallbackToMock;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    public String getAlphaVantageBaseUrl() {
        return alphaVantageBaseUrl;
    }

    public void setAlphaVantageBaseUrl(String alphaVantageBaseUrl) {
        this.alphaVantageBaseUrl = alphaVantageBaseUrl;
    }

    public String getAlphaVantageApiKey() {
        return alphaVantageApiKey;
    }

    public void setAlphaVantageApiKey(String alphaVantageApiKey) {
        this.alphaVantageApiKey = alphaVantageApiKey;
    }

    public String getAlphaVantageEntitlement() {
        return alphaVantageEntitlement;
    }

    public void setAlphaVantageEntitlement(String alphaVantageEntitlement) {
        this.alphaVantageEntitlement = alphaVantageEntitlement;
    }
}
