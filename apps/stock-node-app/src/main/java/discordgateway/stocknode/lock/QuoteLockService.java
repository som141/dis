package discordgateway.stocknode.lock;

import java.util.Optional;

public interface QuoteLockService {

    Optional<QuoteLockHandle> tryAcquire(String market, String symbol);

    void release(QuoteLockHandle quoteLockHandle);
}
