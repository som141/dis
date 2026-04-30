package discordgateway.stocknode.persistence.repository;

import discordgateway.stocknode.persistence.entity.TradeLedgerEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface TradeLedgerRepository extends JpaRepository<TradeLedgerEntity, Long> {

    List<TradeLedgerEntity> findAllByAccountIdOrderByOccurredAtDesc(Long accountId);

    List<TradeLedgerEntity> findAllByAccountIdOrderByOccurredAtDesc(Long accountId, Pageable pageable);
}
