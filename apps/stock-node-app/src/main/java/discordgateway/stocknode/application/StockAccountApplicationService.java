package discordgateway.stocknode.application;

import discordgateway.stocknode.persistence.entity.StockAccountEntity;
import discordgateway.stocknode.persistence.repository.StockAccountRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public class StockAccountApplicationService {

    private final StockAccountRepository stockAccountRepository;
    private final StockSeasonService stockSeasonService;

    public StockAccountApplicationService(
            StockAccountRepository stockAccountRepository,
            StockSeasonService stockSeasonService
    ) {
        this.stockAccountRepository = stockAccountRepository;
        this.stockSeasonService = stockSeasonService;
    }

    @Transactional
    public StockAccountSummary ensureAccount(long guildId, long userId) {
        return toSummary(ensureAccountEntity(guildId, userId));
    }

    @Transactional(readOnly = true)
    public Optional<StockAccountSummary> findAccount(long guildId, long userId) {
        String seasonKey = stockSeasonService.currentSeasonKey();
        return stockAccountRepository.findByGuildIdAndUserIdAndSeasonKey(guildId, userId, seasonKey)
                .map(this::toSummary);
    }

    @Transactional
    public StockAccountEntity ensureAccountEntity(long guildId, long userId) {
        String seasonKey = stockSeasonService.currentSeasonKey();
        return stockAccountRepository.findByGuildIdAndUserIdAndSeasonKey(guildId, userId, seasonKey)
                .orElseGet(() -> createAccount(guildId, userId, seasonKey));
    }

    @Transactional(readOnly = true)
    public Optional<StockAccountEntity> findAccountEntity(long guildId, long userId) {
        return stockAccountRepository.findByGuildIdAndUserIdAndSeasonKey(
                guildId,
                userId,
                stockSeasonService.currentSeasonKey()
        );
    }

    @Transactional(readOnly = true)
    public java.util.List<StockAccountEntity> findActiveSeasonAccountsByGuildId(long guildId) {
        return stockAccountRepository.findAllByGuildIdAndSeasonKeyOrderByIdAsc(
                guildId,
                stockSeasonService.currentSeasonKey()
        );
    }

    @Transactional(readOnly = true)
    public java.util.List<StockAccountEntity> findAllActiveSeasonAccounts() {
        return stockAccountRepository.findAllBySeasonKeyOrderByIdAsc(stockSeasonService.currentSeasonKey());
    }

    @Transactional(readOnly = true)
    public String currentSeasonKey() {
        return stockSeasonService.currentSeasonKey();
    }

    private StockAccountEntity createAccount(long guildId, long userId, String seasonKey) {
        try {
            return stockAccountRepository.saveAndFlush(
                    StockAccountEntity.create(guildId, userId, seasonKey)
            );
        } catch (DataIntegrityViolationException exception) {
            return stockAccountRepository.findByGuildIdAndUserIdAndSeasonKey(guildId, userId, seasonKey)
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
