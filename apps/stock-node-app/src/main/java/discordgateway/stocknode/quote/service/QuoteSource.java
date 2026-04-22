package discordgateway.stocknode.quote.service;

public enum QuoteSource {
    CACHE_FRESH,
    CACHE_STALE,
    PROVIDER_MISS,
    PROVIDER_REFRESH
}
