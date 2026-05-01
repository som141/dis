package discordgateway.stocknode.application;

public record LiquidationBatchResult(
        String symbol,
        int scannedCount,
        int liquidatedCount,
        int failureCount
) {
}
