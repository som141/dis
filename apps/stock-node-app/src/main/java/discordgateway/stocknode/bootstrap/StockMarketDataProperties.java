package discordgateway.stocknode.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "stock.market-data")
public class StockMarketDataProperties {

    private boolean enabled = true;
    private String market = "US";
    private long refreshFixedDelayMs = 20_000L;
    private int topRankLimit = 10;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getMarket() {
        return market;
    }

    public void setMarket(String market) {
        this.market = market;
    }

    public long getRefreshFixedDelayMs() {
        return refreshFixedDelayMs;
    }

    public void setRefreshFixedDelayMs(long refreshFixedDelayMs) {
        this.refreshFixedDelayMs = refreshFixedDelayMs;
    }

    public int getTopRankLimit() {
        return topRankLimit;
    }

    public void setTopRankLimit(int topRankLimit) {
        this.topRankLimit = topRankLimit;
    }
}
