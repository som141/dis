package discordgateway.stocknode.persistence.repository;

import discordgateway.stocknode.persistence.entity.StockWatchlistEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StockWatchlistRepository extends JpaRepository<StockWatchlistEntity, Long> {

    List<StockWatchlistEntity> findAllByMarketIgnoreCaseAndEnabledTrueOrderByRankNoAsc(String market);

    List<StockWatchlistEntity> findAllByMarketIgnoreCaseAndEnabledTrueOrderByRankNoAsc(String market, Pageable pageable);

    Optional<StockWatchlistEntity> findByMarketIgnoreCaseAndSymbolIgnoreCaseAndEnabledTrue(String market, String symbol);
}
