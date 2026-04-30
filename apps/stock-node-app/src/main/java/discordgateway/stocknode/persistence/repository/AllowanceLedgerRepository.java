package discordgateway.stocknode.persistence.repository;

import discordgateway.stocknode.persistence.entity.AllowanceLedgerEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface AllowanceLedgerRepository extends JpaRepository<AllowanceLedgerEntity, Long> {

    List<AllowanceLedgerEntity> findAllByAccountIdOrderByOccurredAtDesc(Long accountId);

    boolean existsByAccountIdAndAllowanceTypeAndOccurredAtGreaterThanEqualAndOccurredAtLessThan(
            Long accountId,
            String allowanceType,
            Instant fromInclusive,
            Instant toExclusive
    );
}
