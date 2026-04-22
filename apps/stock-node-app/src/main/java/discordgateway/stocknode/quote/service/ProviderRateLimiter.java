package discordgateway.stocknode.quote.service;

import java.time.Instant;

public interface ProviderRateLimiter {

    boolean tryConsume(String provider, Instant now);
}
