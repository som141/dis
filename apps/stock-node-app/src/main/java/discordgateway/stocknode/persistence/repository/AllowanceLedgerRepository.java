package discordgateway.stocknode.persistence.repository;

import discordgateway.stocknode.persistence.entity.AllowanceLedgerEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.math.BigDecimal;
import java.util.List;

public interface AllowanceLedgerRepository extends JpaRepository<AllowanceLedgerEntity, Long> {

    List<AllowanceLedgerEntity> findAllByAccountIdOrderByOccurredAtDesc(Long accountId);

    boolean existsByAccountIdAndAllowanceType(Long accountId, String allowanceType);

    boolean existsByAccountIdAndAllowanceTypeAndOccurredAtGreaterThanEqualAndOccurredAtLessThan(
            Long accountId,
            String allowanceType,
            Instant fromInclusive,
            Instant toExclusive
    );

    @Query("select coalesce(sum(a.amount), 0) from AllowanceLedgerEntity a where a.account.id = :accountId")
    BigDecimal sumAmountByAccountId(Long accountId);
}
