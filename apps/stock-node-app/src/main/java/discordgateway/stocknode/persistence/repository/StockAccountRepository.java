package discordgateway.stocknode.persistence.repository;

import discordgateway.stocknode.persistence.entity.StockAccountEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StockAccountRepository extends JpaRepository<StockAccountEntity, Long> {

    Optional<StockAccountEntity> findByGuildIdAndUserIdAndSeasonKey(long guildId, long userId, String seasonKey);

    java.util.List<StockAccountEntity> findAllByGuildIdAndSeasonKeyOrderByIdAsc(long guildId, String seasonKey);

    java.util.List<StockAccountEntity> findAllBySeasonKeyOrderByIdAsc(String seasonKey);
}
