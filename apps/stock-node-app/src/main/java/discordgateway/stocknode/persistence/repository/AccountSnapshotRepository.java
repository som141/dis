package discordgateway.stocknode.persistence.repository;

import discordgateway.stocknode.persistence.entity.AccountSnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AccountSnapshotRepository extends JpaRepository<AccountSnapshotEntity, Long> {

    Optional<AccountSnapshotEntity> findTopByAccountIdOrderBySnapshotAtDesc(Long accountId);
}
