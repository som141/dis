package discordgateway.stocknode.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "stock.quote")
public class StockQuoteProperties {

    private String provider = "mock";
    private String defaultMarket = "us";
    private Duration cacheTtl = Duration.ofSeconds(60);
    private Duration freshness = Duration.ofSeconds(45);
    private Duration tradeFreshness = Duration.ofSeconds(45);
    private Duration rankFreshness = Duration.ofMinutes(5);
    private Duration lockTtl = Duration.ofSeconds(3);
    private Duration lockWaitTimeout = Duration.ofSeconds(1);
    private Duration lockPollInterval = Duration.ofMillis(25);
    private long providerPerMinuteLimit = 60;
    private long providerPerDayLimit = 5_000;

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getDefaultMarket() {
        return defaultMarket;
    }

    public void setDefaultMarket(String defaultMarket) {
        this.defaultMarket = defaultMarket;
    }

    public Duration getCacheTtl() {
        return cacheTtl;
    }

    public void setCacheTtl(Duration cacheTtl) {
        this.cacheTtl = cacheTtl;
    }

    public Duration getFreshness() {
        return freshness;
    }

    public void setFreshness(Duration freshness) {
        this.freshness = freshness;
    }

    public Duration getTradeFreshness() {
        return tradeFreshness;
    }

    public void setTradeFreshness(Duration tradeFreshness) {
        this.tradeFreshness = tradeFreshness;
    }

    public Duration getQueryFreshness() {
        return freshness;
    }

    public void setQueryFreshness(Duration queryFreshness) {
        this.freshness = queryFreshness;
    }

    public Duration getRankFreshness() {
        return rankFreshness;
    }

    public void setRankFreshness(Duration rankFreshness) {
        this.rankFreshness = rankFreshness;
    }

    public Duration getLockTtl() {
        return lockTtl;
    }

    public void setLockTtl(Duration lockTtl) {
        this.lockTtl = lockTtl;
    }

    public Duration getLockWaitTimeout() {
        return lockWaitTimeout;
    }

    public void setLockWaitTimeout(Duration lockWaitTimeout) {
        this.lockWaitTimeout = lockWaitTimeout;
    }

    public Duration getLockPollInterval() {
        return lockPollInterval;
    }

    public void setLockPollInterval(Duration lockPollInterval) {
        this.lockPollInterval = lockPollInterval;
    }

    public long getProviderPerMinuteLimit() {
        return providerPerMinuteLimit;
    }

    public void setProviderPerMinuteLimit(long providerPerMinuteLimit) {
        this.providerPerMinuteLimit = providerPerMinuteLimit;
    }

    public long getProviderPerDayLimit() {
        return providerPerDayLimit;
    }

    public void setProviderPerDayLimit(long providerPerDayLimit) {
        this.providerPerDayLimit = providerPerDayLimit;
    }
}
