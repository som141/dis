package discordgateway.stocknode.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

public class StockMetricsRecorder {

    private final MeterRegistry meterRegistry;
    private final ConcurrentMap<String, AtomicLong> gauges = new ConcurrentHashMap<>();

    public StockMetricsRecorder(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordCommand(String command, String result, Duration duration) {
        Counter.builder("stock.commands")
                .tag("command", normalizeTag(command))
                .tag("result", normalizeTag(result))
                .register(meterRegistry)
                .increment();
        Timer.builder("stock.command.duration")
                .tag("command", normalizeTag(command))
                .tag("result", normalizeTag(result))
                .register(meterRegistry)
                .record(duration);
    }

    public void recordQuoteRefreshSuccess(String provider, String market, String symbol) {
        Counter.builder("stock.quote.refresh.success")
                .tag("provider", normalizeTag(provider))
                .tag("market", normalizeTag(market))
                .tag("symbol", normalizeTag(symbol))
                .register(meterRegistry)
                .increment();
    }

    public void recordQuoteRefreshFailure(String provider, String market, String symbol, String reason) {
        Counter.builder("stock.quote.refresh.failures")
                .tag("provider", normalizeTag(provider))
                .tag("market", normalizeTag(market))
                .tag("symbol", normalizeTag(symbol))
                .tag("reason", normalizeTag(reason))
                .register(meterRegistry)
                .increment();
    }

    public void recordProviderRateLimitExceeded(String provider) {
        Counter.builder("stock.provider.rate.limit.exceeded")
                .tag("provider", normalizeTag(provider))
                .register(meterRegistry)
                .increment();
    }

    public void recordTradeExecution(String side, String market, String symbol) {
        Counter.builder("stock.trade.executions")
                .tag("side", normalizeTag(side))
                .tag("market", normalizeTag(market))
                .tag("symbol", normalizeTag(symbol))
                .register(meterRegistry)
                .increment();
    }

    public void recordTradeRejection(String side, String reason) {
        Counter.builder("stock.trade.rejections")
                .tag("side", normalizeTag(side))
                .tag("reason", normalizeTag(reason))
                .register(meterRegistry)
                .increment();
    }

    public void recordAutoLiquidations(String market, String symbol, int count) {
        if (count <= 0) {
            return;
        }
        Counter.builder("stock.auto.liquidations")
                .tag("market", normalizeTag(market))
                .tag("symbol", normalizeTag(symbol))
                .register(meterRegistry)
                .increment(count);
    }

    public void recordQuoteCacheState(String market, int expectedCount, int readyCount, int staleCount, long oldestAgeSeconds) {
        String normalizedMarket = normalizeTag(market);
        updateGauge("stock.quote.cache.expected", normalizedMarket, Math.max(expectedCount, 0));
        updateGauge("stock.quote.cache.ready", normalizedMarket, Math.max(readyCount, 0));
        updateGauge("stock.quote.cache.stale", normalizedMarket, Math.max(staleCount, 0));
        updateGauge("stock.quote.cache.oldest.age", normalizedMarket, Math.max(oldestAgeSeconds, 0));
    }

    private void updateGauge(String metricName, String market, long value) {
        String key = metricName + "|" + market;
        AtomicLong gaugeValue = gauges.computeIfAbsent(key, ignored -> {
            AtomicLong holder = new AtomicLong();
            Gauge.builder(metricName, holder, AtomicLong::get)
                    .tag("market", market)
                    .register(meterRegistry);
            return holder;
        });
        gaugeValue.set(value);
    }

    private String normalizeTag(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.trim().toLowerCase();
    }
}
