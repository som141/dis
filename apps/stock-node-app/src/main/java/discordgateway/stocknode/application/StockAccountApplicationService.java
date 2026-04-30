package discordgateway.stocknode.application;

import discordgateway.stocknode.persistence.entity.StockAccountEntity;
import discordgateway.stocknode.persistence.repository.StockAccountRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public class StockAccountApplicationService {

    private final StockAccountRepository stockAccountRepository;

    public StockAccountApplicationService(StockAccountRepository stockAccountRepository) {
        this.stockAccountRepository = stockAccountRepository;
    }

    @Transactional
    public StockAccountSummary ensureAccount(long guildId, long userId) {
        return toSummary(ensureAccountEntity(guildId, userId));
    }

    @Transactional(readOnly = true)
    public Optional<StockAccountSummary> findAccount(long guildId, long userId) {
        return stockAccountRepository.findByGuildIdAndUserId(guildId, userId)
                .map(this::toSummary);
    }

    @Transactional
    public StockAccountEntity ensureAccountEntity(long guildId, long userId) {
        return stockAccountRepository.findByGuildIdAndUserId(guildId, userId)
                .orElseGet(() -> createAccount(guildId, userId));
    }

    @Transactional(readOnly = true)
    public Optional<StockAccountEntity> findAccountEntity(long guildId, long userId) {
        return stockAccountRepository.findByGuildIdAndUserId(guildId, userId);
    }

    private StockAccountEntity createAccount(long guildId, long userId) {
        try {
            return stockAccountRepository.saveAndFlush(
                    StockAccountEntity.create(guildId, userId)
            );
        } catch (DataIntegrityViolationException exception) {
            return stockAccountRepository.findByGuildIdAndUserId(guildId, userId)
                    .orElseThrow(() -> exception);
        }
    }

    private StockAccountSummary toSummary(StockAccountEntity entity) {
        return new StockAccountSummary(
                entity.getId(),
                entity.getGuildId(),
                entity.getUserId(),
                entity.getCashBalance()
        );
    }
}
