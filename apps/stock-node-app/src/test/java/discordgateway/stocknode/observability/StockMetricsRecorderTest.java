package discordgateway.stocknode.observability;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class StockMetricsRecorderTest {

    @Test
    void recordsStockMetricsWithBoundedLabels() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        StockMetricsRecorder recorder = new StockMetricsRecorder(meterRegistry);

        recorder.recordCommand("BUY", "SUCCESS", Duration.ofMillis(25));
        recorder.recordQuoteRefreshSuccess("Finnhub", "US", "NVDA");
        recorder.recordQuoteRefreshFailure("Finnhub", "US", "NVDA", "RuntimeException");
        recorder.recordProviderRateLimitExceeded("Finnhub");
        recorder.recordTradeExecution("BUY", "US", "NVDA");
        recorder.recordTradeRejection("SELL", "StaleQuoteException");
        recorder.recordAutoLiquidations("US", "NVDA", 2);
        recorder.recordQuoteCacheState("US", 10, 8, 2, 44);

        assertThat(meterRegistry.counter("stock.commands", "command", "buy", "result", "success").count())
                .isEqualTo(1.0);
        assertThat(meterRegistry.find("stock.command.duration").timer()).isNotNull();
        assertThat(meterRegistry.counter(
                "stock.quote.refresh.success",
                "provider", "finnhub",
                "market", "us",
                "symbol", "nvda"
        ).count()).isEqualTo(1.0);
        assertThat(meterRegistry.counter(
                "stock.quote.refresh.failures",
                "provider", "finnhub",
                "market", "us",
                "symbol", "nvda",
                "reason", "runtimeexception"
        ).count()).isEqualTo(1.0);
        assertThat(meterRegistry.counter(
                "stock.provider.rate.limit.exceeded",
                "provider", "finnhub"
        ).count()).isEqualTo(1.0);
        assertThat(meterRegistry.counter(
                "stock.trade.executions",
                "side", "buy",
                "market", "us",
                "symbol", "nvda"
        ).count()).isEqualTo(1.0);
        assertThat(meterRegistry.counter(
                "stock.trade.rejections",
                "side", "sell",
                "reason", "stalequoteexception"
        ).count()).isEqualTo(1.0);
        assertThat(meterRegistry.counter(
                "stock.auto.liquidations",
                "market", "us",
                "symbol", "nvda"
        ).count()).isEqualTo(2.0);
        assertThat(meterRegistry.find("stock.quote.cache.expected").tag("market", "us").gauge().value())
                .isEqualTo(10.0);
        assertThat(meterRegistry.find("stock.quote.cache.ready").tag("market", "us").gauge().value())
                .isEqualTo(8.0);
        assertThat(meterRegistry.find("stock.quote.cache.stale").tag("market", "us").gauge().value())
                .isEqualTo(2.0);
        assertThat(meterRegistry.find("stock.quote.cache.oldest.age").tag("market", "us").gauge().value())
                .isEqualTo(44.0);
    }
}
