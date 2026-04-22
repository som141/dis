package discordgateway.stocknode.lock;

public record QuoteLockHandle(
        String key,
        String ownerToken
) {
}
