package discordgateway.stocknode.persistence.repository;

import discordgateway.stocknode.persistence.entity.StockPositionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StockPositionRepository extends JpaRepository<StockPositionEntity, Long> {

    Optional<StockPositionEntity> findByAccountIdAndSymbol(Long accountId, String symbol);

    List<StockPositionEntity> findAllByAccountIdOrderBySymbolAsc(Long accountId);

    List<StockPositionEntity> findAllBySymbolOrderByIdAsc(String symbol);
}
