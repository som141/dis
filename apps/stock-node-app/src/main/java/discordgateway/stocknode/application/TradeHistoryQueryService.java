package discordgateway.stocknode.application;

import discordgateway.stocknode.persistence.entity.StockAccountEntity;
import discordgateway.stocknode.persistence.entity.TradeLedgerEntity;
import discordgateway.stocknode.persistence.repository.TradeLedgerRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public class TradeHistoryQueryService {

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 20;

    private final DailyAllowanceService dailyAllowanceService;
    private final TradeLedgerRepository tradeLedgerRepository;

    public TradeHistoryQueryService(
            DailyAllowanceService dailyAllowanceService,
            TradeLedgerRepository tradeLedgerRepository
    ) {
        this.dailyAllowanceService = dailyAllowanceService;
        this.tradeLedgerRepository = tradeLedgerRepository;
    }

    @Transactional
    public TradeHistoryView getHistory(long guildId, long userId, Integer limit) {
        StockAccountEntity account = dailyAllowanceService.ensureSettledAccount(guildId, userId);
        int normalizedLimit = normalizeLimit(limit);
        List<TradeHistoryEntryView> entries = tradeLedgerRepository
                .findAllByAccountIdOrderByOccurredAtDesc(
                        account.getId(),
                        PageRequest.of(0, normalizedLimit)
                )
                .stream()
                .map(this::toEntry)
                .toList();
        return new TradeHistoryView(
                account.getId(),
                account.getGuildId(),
                account.getUserId(),
                account.getCashBalance(),
                entries
        );
    }

    private TradeHistoryEntryView toEntry(TradeLedgerEntity entity) {
        return new TradeHistoryEntryView(
                entity.getSymbol(),
                TradeSide.valueOf(entity.getSide()),
                entity.getQuantity(),
                entity.getUnitPrice(),
                entity.getLeverage(),
                entity.getMarginAmount(),
                entity.getNotionalAmount(),
                entity.getOccurredAt()
        );
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }
}
